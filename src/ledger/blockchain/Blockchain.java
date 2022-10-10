package ledger.blockchain;

import cyclops.control.Trampoline;
import ledger.Ledger;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import main.GlobalProperties;
import mempoolManager.BootstrapModule;
import mempoolManager.MempoolChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import validators.ApplicationObliviousValidator;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static cyclops.control.Trampoline.more;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.util.stream.Collectors.*;

public class Blockchain implements Ledger {

	public static final int FINALIZED_WEIGHT = 6;
	private static final Logger logger = LogManager.getLogger(Blockchain.class.getName());
	private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors() / 4;
	private static final long WAIT_DELAY_REORDER = 1000 * 5;
	private static final long VERIFICATION_INTERVAL = WAIT_DELAY_REORDER * 5;
	private static final ScheduledThreadPoolExecutor pool = initPool();
	public final int finalizedWeight;
	//Kept public to be accessed by the tests, this is not used elsewhere.
	public final Map<UUID, BlockchainNode> blocks = new HashMap<>();
	//Only used in the unit tests
	public final Map<UUID, BlockchainNode> finalized = new HashMap<>();
	//Blocks that have arrived but are yet to be processed.
	//This Blockchain implementation is fundamentally serial.
	//Blocks are kept in this queue and a single thread will process each individually in a FIFO order.
	private final BlockingQueue<BlockmessBlock> toProcess = new LinkedBlockingQueue<>();
	//Collection containing the blocks that have no other block referencing them.
	//Effectively being the tips of their forks.
	private final Map<UUID, BlockchainNode> chainTips = new HashMap<>();
	private final List<LedgerObserver> observers = new LinkedList<>();
	private final DelayVerifier delayVerifier;
	private final ScheduledFuture<?> task;
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private BlockchainNode priorityTip;
	private BlockchainNode lastFinalized;

	public Blockchain() {
		this(computeGenesisUUID());
	}

	private static UUID computeGenesisUUID() {
		Properties props = GlobalProperties.getProps();
		String genesisUUIDStr = props.getProperty("genesisUUID",
				"00000000-0000-0000-0000-000000000000");
		return UUID.fromString(genesisUUIDStr);
	}

	public Blockchain(UUID genesisUUID) {
		Properties props = GlobalProperties.getProps();
		this.finalizedWeight = parseInt(props.getProperty("finalizedWeight",
				String.valueOf(FINALIZED_WEIGHT)));
		createGenesisBlock(genesisUUID);
		bootstrapBlockchain(BootstrapModule.getStoredChunks());
		this.delayVerifier = generateDelayVerifier(props);
		int expectedTimeBetweenBlocks = Integer.parseInt(props.getProperty("expectedTimeBetweenBlocks"));
		this.task = pool.scheduleAtFixedRate(new QueuePoller(), expectedTimeBetweenBlocks / 5, expectedTimeBetweenBlocks / 5, TimeUnit.MILLISECONDS);

	}

	private DelayVerifier generateDelayVerifier(Properties props) {
		long waitDelayReorder = parseLong(props.getProperty("waitDelayReorder",
				String.valueOf(WAIT_DELAY_REORDER)));
		long verificationDelay = parseLong(props.getProperty("verificationDelay",
				String.valueOf(VERIFICATION_INTERVAL)));
		return new DelayVerifier(waitDelayReorder, verificationDelay, this);
	}

	private void createGenesisBlock(UUID genesisUUID) {
		BlockchainNode genesis = new BlockchainNode(genesisUUID,
				Collections.emptySet(), finalizedWeight);
		chainTips.put(genesis.getBlockId(), genesis);
		blocks.put(genesis.getBlockId(), genesis);
		finalized.put(genesis.getBlockId(), genesis);
		lastFinalized = genesis;
		priorityTip = genesis;
	}

	private void bootstrapBlockchain(List<MempoolChunk> chunks) {
		for (MempoolChunk chunk : chunks) {
			BlockchainNode block = new BlockchainNode(chunk.getId(), chunk.getPreviousIds(), chunk.getWeight());
			block.getPrevious().forEach(chainTips::remove);
			block.getPrevious().stream().map(blocks::get)
					.forEach(b -> b.getFollowing().add(block.getBlockId()));
			chainTips.put(block.getBlockId(), block);
			blocks.put(block.getBlockId(), block);
			finalized.put(block.getBlockId(), block);
			lastFinalized = block;
			priorityTip = block;
		}
	}

	private static ScheduledThreadPoolExecutor initPool() {
		ScheduledThreadPoolExecutor pool = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(POOL_SIZE);
		pool.setRemoveOnCancelPolicy(true);
		return pool;
	}

	//DO NOT PLACE LOCK HERE: DEADLOCK WILL ENSUE
	//THERE IS A CYCLIC DEPENDENCY BETWEEN THIS CLASS AND THE DELAYVERIFIER (sorry)
	boolean hasBlock(UUID blockId) {
		return blocks.containsKey(blockId);
	}

	private Set<UUID> getForkBlocks(Set<UUID> curr, int depth) {
		return recursiveGetForkBlocks(curr, depth).result();
	}

	private Trampoline<Set<UUID>> recursiveGetForkBlocks(Set<UUID> curr, int depth) {
		if (depth == 0)
			return Trampoline.done(curr);
		Set<UUID> prev = curr.stream()
				.map(blocks::get)
				.map(BlockchainNode::getPrevious)
				.flatMap(Collection::stream)
				.collect(toSet());
		return more(() -> recursiveGetForkBlocks(prev, depth - 1));
	}

	@Override
	public Set<UUID> getBlockR() {
		assert !chainTips.isEmpty();
		try {
			lock.readLock().lock();
			return Set.of(priorityTip.getBlockId());
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public void submitBlock(BlockmessBlock block) {
		logger.debug("Received block {}, referencing {}", block.getBlockId(), block.getPrevRefs());
		toProcess.add(block);
		processBlocks();
	}

	@Override
	public void attachObserver(LedgerObserver observer) {
		this.observers.add(observer);
	}

	public Set<UUID> getFollowing(UUID block, int distance) {
		if (distance < 0 || blocks.get(block) == null)
			throw new IllegalArgumentException();
		return distance == 0 ? Set.of(block) :
				blocks.get(block)
						.getFollowing()
						.stream()
						.map(b -> getFollowing(b, distance - 1))
						.flatMap(Collection::stream)
						.collect(toSet());
	}

	@Override
	public int getWeight(UUID block) throws IllegalArgumentException {
		BlockchainNode node = blocks.get(block);
		if (node == null) throw new IllegalArgumentException();
		return node.getWeight();
	}

	@Override
	public boolean isInLongestChain(UUID nodeId) {
		BlockchainNode node = blocks.get(nodeId);
		if (node == null)
			return false;
		if (chainTips.containsKey(nodeId))
			return node.getWeight() == getMaxWeight();
		return node.getFollowing().stream().anyMatch(this::isInLongestChain);
	}

	public int getFinalizedWeight() {
		return finalizedWeight;
	}

	@Override
	public Set<UUID> getForkBlocks(int depth) throws IllegalArgumentException {
		if (depth < 0)
			throw new IllegalArgumentException();
		return getForkBlocks(getBlockR(), depth);
	}

	@Override
	public void close() {
		task.cancel(false);
		delayVerifier.close();
	}

	private void processBlocks() {
		if (!toProcess.isEmpty()) {
			try {
				lock.writeLock().lock();
				tryToProcessBlocks();
			} finally {
				lock.writeLock().unlock();
			}
		}
	}

	public void tryToProcessBlocks() {
		while (!toProcess.isEmpty()) {
			try {
				processBlock(toProcess.take());
			} catch (InterruptedException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	}

	private void processBlock(BlockmessBlock block) {
		long start = System.currentTimeMillis();
		List<UUID> prev = block.getPrevRefs();
		if (prev.size() != 1) {
			logger.info("Received malformed block with id: {}", block.getBlockId());
		} else if (!blocks.containsKey(prev.get(0))) {
			logger.info("Received unordered block with id: {}, referencing {}",
					block.getBlockId(), block.getPrevRefs().get(0));
			delayVerifier.submitUnordered(block);
		} else if (ApplicationObliviousValidator.getSingleton().isBlockValid(block)) {
			processValidBlock(block);
		} else {
			logger.info("Received invalid block {} referencing {}",
					block.getBlockId(), block.getPrevRefs().get(0));
		}
		long end = System.currentTimeMillis();
		logger.info("Elapsed time in processing block {}: {} miliseconds",
				block.getBlockId(), (end - start));
	}

	private void processValidBlock(BlockmessBlock block) {
		List<UUID> prev = block.getPrevRefs();
		logger.debug("Processing valid block {}", block.getBlockId());
		int weight = blocks.get(prev.get(0)).getWeight() + block.getInherentWeight();
		addBlock(new BlockchainNode(block.getBlockId(),
				new HashSet<>(prev), weight));
		deliverNonFinalizedBlock(block, weight);
	}

	private void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
		for (LedgerObserver observer : this.observers)
			observer.deliverNonFinalizedBlock(block, weight);
	}

	private void addBlock(BlockchainNode block) {
		BlockchainNode prev = blocks.get(block.getPrevious().iterator().next());
		prev.getFollowing().add(block.getBlockId());
		chainTips.remove(prev.getBlockId());
		chainTips.put(block.getBlockId(), block);
		if (block.getWeight() > priorityTip.getWeight())
			priorityTip = block;
		logger.debug("Inserting: {}", block.getBlockId());
		blocks.put(block.getBlockId(), block);
		finalized.putAll(finalizeBlocks().stream().collect(toMap(BlockchainNode::getBlockId, b -> b)));
	}

	private List<BlockchainNode> finalizeBlocks() {
		Set<UUID> deleted = deleteForkedChains();
		List<BlockchainNode> finalized = finalizeForward();
		List<UUID> finalizedIds = finalized.stream().map(BlockchainNode::getBlockId).collect(toList());
		deliverFinalizedBlocks(finalizedIds, deleted);
		return finalized;
	}

	private void deliverFinalizedBlocks(List<UUID> finalizedIds, Set<UUID> deleted) {
		for (LedgerObserver observer : this.observers)
			observer.deliverFinalizedBlocks(finalizedIds, deleted);
	}

	/*
	 * The collect operation is required because otherwise we'd be removing elements from the same DS that is being iterated
	 */
	private Set<UUID> deleteForkedChains() {
		assert !chainTips.isEmpty();
		int maxChainWeight = chainTips.values().stream()
				.mapToInt(BlockchainNode::getWeight).max()
				.getAsInt();
		List<BlockchainNode> toRemoveTips = chainTips.values().stream()
				.filter(b -> maxChainWeight - b.getWeight() >= finalizedWeight)
				.collect(toList());
		Set<UUID> deletedBlocks = new HashSet<>();
		for (BlockchainNode tip : toRemoveTips)
			deletedBlocks.addAll(deleteForkedChain(tip));
		return deletedBlocks;
	}

	private Set<UUID> deleteForkedChain(BlockchainNode chainTip) {
		Set<UUID> deleted = new HashSet<>();
		chainTips.remove(chainTip.getBlockId());
		BlockchainNode currentBlock = chainTip;
		BlockchainNode previousBlock = null;
		while (currentBlock.getFollowing().size() < 2) {
			blocks.remove(currentBlock.getBlockId());
			deleted.add(currentBlock.getBlockId());
			logger.debug("Removing: {}", currentBlock.getBlockId());
			previousBlock = currentBlock;
			currentBlock = blocks.get(currentBlock.getPrevious().iterator().next());
		}
		assert previousBlock != null;
		currentBlock.getFollowing().remove(previousBlock.getBlockId());
		return deleted;
	}

	private List<BlockchainNode> finalizeForward() {
		assert !chainTips.isEmpty();
		int maxWeight = chainTips.values().stream()
				.mapToInt(BlockchainNode::getWeight).max().getAsInt();
		List<BlockchainNode> newlyFinalized = new LinkedList<>();
		if (lastFinalized.getFollowing().size() < 2
				&& maxWeight - blocks.get(lastFinalized.getFollowing().iterator().next()).getWeight() >= finalizedWeight)
			do {
				lastFinalized = blocks.get(lastFinalized.getFollowing().iterator().next());
				logger.debug("Finalizing: {}", lastFinalized.getBlockId());
				newlyFinalized.add(lastFinalized);
			} while (lastFinalized.getFollowing().size() < 2 && maxWeight - lastFinalized.getWeight() > finalizedWeight);

		return newlyFinalized;
	}

	private int getMaxWeight() {
		try {
			lock.readLock().lock();
			return chainTips.values().stream()
					.mapToInt(BlockchainNode::getWeight)
					.max().orElse(0);
		} finally {
			lock.readLock().unlock();
		}
	}

	private class QueuePoller extends Thread {

		public void run() {
			toProcess.addAll(delayVerifier.getOrderedBlocks());
			processBlocks();
		}

	}

}
