package ledger.ledgerManager;

import applicationInterface.GlobalProperties;
import cmux.AppOperation;
import ledger.Ledger;
import ledger.LedgerObserver;
import ledger.blockchain.BlockFinalizer;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import ledger.ledgerManager.nodes.BlockmessChain;
import ledger.ledgerManager.nodes.ParentTreeNode;
import ledger.ledgerManager.nodes.ReferenceNode;
import lombok.Getter;
import operationMapper.ComposableOperationMapperImp;
import operationMapper.OperationMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import validators.FixedApplicationAwareValidator;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class LedgerManager implements ParentTreeNode, Ledger, LedgerObserver, OperationMapper {

	private static final Logger logger = LogManager.getLogger(LedgerManager.class);
	private static LedgerManager singleton;

	@Getter
	private final Map<UUID, BlockmessChain> chains = Collections.synchronizedMap(new LinkedHashMap<>());
	private final BlockingQueue<BlockmessChain> toCreateChains = new LinkedBlockingQueue<>();
	private final BlockingQueue<UUID> toRemoveChains = new LinkedBlockingQueue<>();
	private final List<LedgerObserver> observers = new LinkedList<>();
	private final int finalizedWeight;
	private final int minNumChains;
	private final int maxNumChains;
	private final BlockingQueue<Object> deliverFinalizedRequests = new LinkedBlockingQueue<>();

	private final List<ChainChangeObserver> chainChangeObservers = new LinkedList<>();
	private long confirmBar = 0;

	private LedgerManager() {
		this(computeOgChainId());
	}

	private LedgerManager(UUID ogChainId) {
		Properties props = GlobalProperties.getProps();
		var originChain = new ReferenceNode(props, ogChainId, this, 0, 1, 0,
				new ComposableOperationMapperImp());
		originChain.attachObserver(this);
		this.minNumChains = parseInt(props.getProperty("minNumChains", "1"));
		this.maxNumChains = parseInt(props.getProperty("maxNumChains", String.valueOf(Integer.MAX_VALUE)));
		int initialNumChains = parseInt(props.getProperty("initialNumChains", String.valueOf(minNumChains)));
		initializeChains(originChain, initialNumChains);
		this.finalizedWeight = parseInt(props.getProperty("finalizedWeight", String.valueOf(BlockFinalizer.FINALIZED_WEIGHT)));
		new Thread(this::processBlockDeliveries).start();
	}

	private void initializeChains(BlockmessChain origin, int initialNumChains) {
		int seedCounter = 1;
		this.chains.put(origin.getChainId(), origin);
		List<BlockmessChain> prevRoundChains = List.of(origin);
		while (chains.size() < initialNumChains) {
			Iterator<BlockmessChain> chainIterator = prevRoundChains.iterator();
			while (chainIterator.hasNext() && chains.size() + toCreateChains.size() < initialNumChains) {
				BlockmessChain chain = chainIterator.next();
				chain.spawnPermanentChildren(getRandomUUIDFromSeed(seedCounter++),
						getRandomUUIDFromSeed(seedCounter++));
			}
			prevRoundChains = new ArrayList<>(chains.values());
			addNewChains();
		}
		chainChangeObservers.forEach(observer -> observer.notifyChangesChains(chains.size()));
	}

	private UUID getRandomUUIDFromSeed(int seed) {
		Random r = new Random(seed);
		return new UUID(r.nextLong(), r.nextLong());
	}

	private void addNewChains() {
		while (!toCreateChains.isEmpty()) {
			try {
				BlockmessChain chain = toCreateChains.take();
				chains.put(chain.getChainId(), chain);
				chain.attachObserver(this);
				logger.info("Creating Chain: {}", chain.getChainId());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void processBlockDeliveries() {
		while (true) {
			try {
				deliverFinalizedRequests.take();
				deliverFinalizedRequests.clear();
				deliverFinalizedBlocksAsync();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void deliverFinalizedBlocksAsync() {
		int initialNumChains = chains.size();
		removeObsoleteChains();
		addNewChains();
		int finalNumChains = chains.size();
		if (finalNumChains != initialNumChains)
			chainChangeObservers.forEach(observer -> observer.notifyChangesChains(finalNumChains));
		for (BlockmessChain chain : chains.values())
			logger.debug("Chain {} has {} finalized blocks pending, minNextRank is {}, next block has rank {}",
					chain.getChainId(), chain.getNumFinalizedPending(), chain.getNextRank(),
					chain.hasFinalized() ? chain.peekFinalized().getBlockRank() : -1);
		List<BlockmessBlock> linearizedFinalized = linearizeFinalizedBlocksInChains();
		assert linearizedFinalized != null;
		List<UUID> linearizedUUID = linearizedFinalized.stream().map(BlockmessBlock::getBlockId).collect(toList());
		for (var observer : observers)
			observer.deliverFinalizedBlocks(linearizedUUID, emptySet());
	}

	private List<BlockmessBlock> linearizeFinalizedBlocksInChains() {
		try {
			return tryToLinearizeFinalizedBlocksInChains();
		} catch (LedgerTreeNodeDoesNotExistException e) {
			e.printStackTrace();
			return emptyList();
		}
	}

	private List<BlockmessBlock> tryToLinearizeFinalizedBlocksInChains() throws LedgerTreeNodeDoesNotExistException {
		List<BlockmessBlock> res = new LinkedList<>();
		List<BlockmessBlock> confirmed;
		do {
			confirmBar = computeNextConfirmBar();
			confirmed = confirmBlocksWithRankEqualToConfirmationBar();
			res.addAll(confirmed);
		} while (!confirmed.isEmpty());
		return res;
	}

	private List<BlockmessBlock> confirmBlocksWithRankEqualToConfirmationBar() throws LedgerTreeNodeDoesNotExistException {
		List<BlockmessBlock> res = new LinkedList<>();
		Iterator<BlockmessChain> chainIterator = chains.values().iterator();
		while (chainIterator.hasNext()) {
			Optional<BlockmessChain> chainOp = findNextChainToDeliver(chainIterator);
			if (chainOp.isPresent()) {
				BlockmessChain chain = chainOp.get();
				BlockmessBlock nextDeliver = chain.deliverChainBlock();
				res.add(nextDeliver);
				logger.info("Finalizing block {} in Chain {}", nextDeliver.getBlockId(), chain.getChainId());
				logger.debug("Number overloaded recent finalized blocks: {}", chain.getNumOverloaded());
				logger.debug("Number underloaded recent finalized blocks: {}", chain.getNumUnderloaded());
				if (chain.shouldSpawn() && countReferencedPermanent() <= maxNumChains - 2)
					chain.spawnChildren(nextDeliver.getBlockId());
				else {
					discardSuccessiveChains();
					chainIterator = chains.values().iterator();
				}
			}
		}
		return res;
	}

	private void discardSuccessiveChains() throws LedgerTreeNodeDoesNotExistException {
		boolean mergeFound = true;
		int numCanRemove = getAvailableChains().size() - minNumChains;
		List<BlockmessChain> toResetSamples = new LinkedList<>();
		while (numCanRemove > 0 && mergeFound) {
			mergeFound = false;
			List<UUID> toRemove = new LinkedList<>();
			for (BlockmessChain chain : chains.values())
				if (chain.shouldMerge() && numCanRemove > 0) {
					toResetSamples.add(chain);
					Set<UUID> merged = chain.mergeChildren();
					numCanRemove -= merged.size();
					toRemove.addAll(merged);
					logger.info("Merging Chains: {}", toRemove);
					mergeFound = true;
				}
			toRemove.forEach(chains::remove);
		}
		toResetSamples.forEach(BlockmessChain::resetSamples);
	}

	public List<BlockmessChain> getAvailableChains() {
		return tryToGetAvailableChains();
	}

	@NotNull
	private List<BlockmessChain> tryToGetAvailableChains() {
		List<BlockmessChain> allChains = List.copyOf(chains.values());
		Set<UUID> preferable = getOrigin().getPriorityChains().stream()
				.map(BlockmessChain::getChainId).collect(toSet());
		return allChains.stream()
				.filter(b -> preferable.contains(b.getChainId()))
				.collect(toList());
	}

	public BlockmessChain getOrigin() {
		return chains.entrySet().iterator().next().getValue();
	}

	private Optional<BlockmessChain> findNextChainToDeliver(Iterator<BlockmessChain> chainIterator) {
		while (chainIterator.hasNext()) {
			BlockmessChain chain = chainIterator.next();
			BlockmessBlock headBlock = chain.peekFinalized();
			if (headBlock != null && headBlock.getBlockRank() < confirmBar)
				return Optional.of(chain);
		}
		return Optional.empty();
	}

	private int countReferencedPermanent() {
		return chains.values().stream().mapToInt(BlockmessChain::countReferencedPermanent).sum() + 1;
	}

	private long computeNextConfirmBar() {
		OptionalLong min = chains.values().stream()
				.mapToLong(BlockmessChain::getNextRank)
				.min();
		return min.orElse(confirmBar);
	}

	private void removeObsoleteChains() {
		while (!toRemoveChains.isEmpty()) {
			try {
				chains.remove(toRemoveChains.take());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static UUID computeOgChainId() {
		Properties props = GlobalProperties.getProps();
		String ogChainIdStr = props.getProperty("ogChainId",
				"00000000-0000-0000-0000-000000000000");
		return UUID.fromString(ogChainIdStr);
	}

	public static LedgerManager getSingleton() {
		if (singleton == null)
			singleton = new LedgerManager();
		return singleton;
	}

	public void addChangesChainsObserver(ChainChangeObserver observer) {
		chainChangeObservers.add(observer);
	}

	@Override
	public Set<UUID> getBlockR() {
		return chains.values().iterator().next().getBlockR();
	}

	@Override
	public void submitBlock(BlockmessBlock block) {
		UUID destinationChain = block.getDestinationChain();
		chains.get(destinationChain).submitBlock(block);
	}

	@Override
	public void attachObserver(LedgerObserver observer) {
		this.observers.add(observer);
	}

	@Override
	public Set<UUID> getFollowing(UUID block, int distance) throws IllegalArgumentException {
		return chains.entrySet().iterator().next().getValue().getFollowing(block, distance);
	}

	@Override
	public int getWeight(UUID block) throws IllegalArgumentException {
		return chains.values().stream()
				.map(b -> {
					try {
						return b.getWeight(block);
					} catch (IllegalArgumentException e) {
						return null;
					}
				}).filter(Objects::nonNull).mapToInt(i -> i).iterator().next();
	}

	@Override
	public boolean isInLongestChain(UUID nodeId) {
		return chains.values().stream().anyMatch(b -> b.isInLongestChain(nodeId));
	}

	@Override
	public int getFinalizedWeight() {
		return finalizedWeight;
	}

	@Override
	public Set<UUID> getForkBlocks(int depth) {
		return chains.values().iterator().next().getForkBlocks(1);
	}

	@Override
	public void replaceChild(BlockmessChain newChild) {
	}

	@Override
	public void forgetUnconfirmedChains(Set<UUID> discartedChainsIds) {
		toRemoveChains.addAll(discartedChainsIds);
	}

	@Override
	public void createChains(List<BlockmessChain> createdChains) {
		toCreateChains.addAll(createdChains);
	}

	@Override
	public ParentTreeNode getTreeRoot() {
		return this;
	}

	@Override
	public void deliverNonFinalizedBlock(BlockmessBlock nonFinalized, int weight) {
		for (LedgerObserver observer : observers)
			observer.deliverNonFinalizedBlock(nonFinalized, weight);
	}

	@Override
	public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
		tryToPlaceFinalizationRequest();
		//Blocks are not delivered to the observers here
		//They are delivered by the thread responsible for the finalization and chain number update
	}

	private void tryToPlaceFinalizationRequest() {
		try {
			deliverFinalizedRequests.put(new Object());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public long getHighestSeenRank() {
		long maxRank = Long.MIN_VALUE;
		for (BlockmessChain chain : chains.values()) {
			long ChainMaxRank = chain.getRankFromRefs(chain.getBlockR());
			maxRank = Math.max(maxRank, ChainMaxRank);
		}
		return maxRank;
	}

	@Override
	public List<AppOperation> generateOperationList(Collection<UUID> states, int usedSpace)
			throws IOException {
		return getOrigin().generateOperationList(states, usedSpace);
	}

	@Override
	public void submitOperations(Collection<AppOperation> operations) {
		getOrigin().submitOperations(operations);
	}

	@Override
	public void submitOperation(AppOperation operation) {
		var isValid = FixedApplicationAwareValidator.getSingleton().validateReceivedOperation(operation.getContent());
		if (isValid.getLeft())
			getOrigin().submitOperation(operation);
	}

	@Override
	public void deleteOperations(Set<UUID> operatationIds) {
		getOrigin().deleteOperations(operatationIds);
	}

	@Override
	public Collection<AppOperation> getStoredOperations() {
		return chains.values().stream()
				.map(OperationMapper::getStoredOperations)
				.flatMap(Collection::stream)
				.collect(toList());
	}
}
