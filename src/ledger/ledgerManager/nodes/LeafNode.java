package ledger.ledgerManager.nodes;

import cmux.AppOperation;
import cmux.CMuxMask;
import ledger.Ledger;
import ledger.LedgerObserver;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.ContentList;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import lombok.experimental.Delegate;
import operationMapper.BaseOperationMapper;
import operationMapper.ComposableOperationMapper;
import operationMapper.ComposableOperationMapperImp;
import operationMapper.OperationMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * This node processes a Chain as if it has not yet spawned any other Chains.
 * <p>Logic regarding the maintenance of other Chains is relegated to other nodes.</p>
 * <p>This node communicates directly with the inner ledger implementation directly
 * (most likely a {@link ledger.blockchain.Blockchain}) and maintains a buffer of finalized blocks to aid the delivered
 * block linearization process undertook by the {@link ledger.ledgerManager.LedgerManager}.</p>
 */
public class LeafNode implements BlockmessChain, LedgerObserver {

	private static final Logger logger = LogManager.getLogger(LeafNode.class);

	private final Properties props;

	private final UUID chainId;

	@Delegate(excludes = ExcludeInnerLedger.class)
	private final Ledger ledger;

	private final List<LedgerObserver> observers = new LinkedList<>();

	private final ReadWriteLock observersLock = new ReentrantReadWriteLock();
	/**
	 * Contains a mapping between all the blocks' ids and themselves.
	 * <p>Used because the inner ledger does not provide the finalized blocks, only their identifier.
	 * However the blocks pass through this object before being finalized.</p>
	 * <p>Shouldn't need to be concurrent if the inner {@link Ledger} is a {@link ledger.blockchain.Blockchain},
	 * however, in case the ledger implementation is changed, this will be kept as concurrent.</p>
	 */
	private final Map<UUID, BlockmessBlock> blocks = new ConcurrentHashMap<>();

	@Delegate(excludes = ExcludeOperationMapper.class)
	private final ComposableOperationMapper operationMapper;
	/**
	 * Stores the finalized blocks on this Chain.
	 * <p>Added as they are finalized in the ledger and removed when they are delivered to the application.</p>
	 */
	private final Queue<BlockmessBlock> finalizedBuffer = new ConcurrentLinkedQueue<>();
	/**
	 * Ledger<BlockmessBlockImp<C,P>> ledger Number of samples used to determine if the Chain should spawn new Chains or
	 * merge into its parent.
	 * <p>The greater the sample size, the more resilient it is towards adversarial intents,
	 * but the least sensible it is to adapt to changes in the load of the system.</p>
	 */
	private final int blocksSampleSize;
	/**
	 * Within the last blocksSampleSize blocks delivered to the application, which were overloaded.
	 */
	private final List<Boolean> overloadedBlocksSample = Collections.synchronizedList(new LinkedList<>());
	/**
	 * Within the last blocksSampleSize blocks delivered to the application, which were underloaded.
	 */
	private final List<Boolean> underloadedBlocksSample = Collections.synchronizedList(new LinkedList<>());
	/**
	 * The maximum allowed size for a block
	 */
	private final int maxBlockSize;
	/**
	 * The percentage of the maxBlockSize size a block must exceed in order to be deemed overloaded.
	 */
	private final float overloadThreshold;
	/**
	 * The percentage of the maxBlockSize size a block mustn't exceed in order to be deemed underloaded.
	 */
	private final float underloadedThreshold;
	private final long minRank;
	private ParentTreeNode parent;
	private long minNextRank;

	private int blocksBeforeResumingMetrics = 0;

	/**
	 * How deep is this Chain in the Blockmess Tree
	 */
	private int depth;

	public LeafNode(
			Properties props, UUID chainId, ParentTreeNode parent,
			long minRank, long minNextRank, int depth, ComposableOperationMapper operationMapper) {
		this.props = props;
		this.chainId = chainId;
		this.ledger = new Blockchain(chainId);
		ledger.attachObserver(this);
		this.parent = parent;
		this.overloadThreshold = parseFloat(props.getProperty("overloadThreshold", "0.9f"));
		this.underloadedThreshold = parseFloat(props.getProperty("underloadedThreshold", "0.4f"));
		this.blocksSampleSize = parseInt(props.getProperty("blockSampleSize", "15"));
		this.maxBlockSize = parseInt(props.getProperty("maxBlockSize", "21000"));
		this.minRank = minRank;
		this.minNextRank = minNextRank;
		this.depth = depth;
		this.operationMapper = operationMapper;
	}

	@Override
	public UUID getChainId() {
		return chainId;
	}

	@Override
	public void replaceParent(ParentTreeNode parent) {
		this.parent = parent;
	}

	@Override
	public void spawnChildren(UUID originator) {
		CMuxMask mask = new CMuxMask(depth);
		OperationMapper lft = new BaseOperationMapper();
		OperationMapper rgt = new BaseOperationMapper();
		depth++;
		var spawnedChainDirectors = operationMapper.separateOperations(mask, lft, rgt);
		var encapsulating = new TempChainNode(props, this, parent, originator, depth, spawnedChainDirectors);
		parent.replaceChild(encapsulating);
		this.parent = encapsulating;
		resetSamples();
	}

	@Override
	public Set<UUID> mergeChildren() throws LedgerTreeNodeDoesNotExistException {
		throw new LedgerTreeNodeDoesNotExistException(chainId);
	}

	@Override
	public boolean isLeaf() {
		return true;
	}

	@Override
	public boolean hasFinalized() {
		return !finalizedBuffer.isEmpty();
	}

	@Override
	public BlockmessBlock peekFinalized() {
		return finalizedBuffer.peek();
	}

	@Override
	public BlockmessBlock deliverChainBlock() {
		BlockmessBlock block = finalizedBuffer.poll();
		if (block != null) {
			computeBlockSizeStatistics(block);
			updateNextRank();
		}
		return block;
	}

	private void computeBlockSizeStatistics(BlockmessBlock block) {
		if (blocksBeforeResumingMetrics > 0)
			blocksBeforeResumingMetrics--;
		else {
			int contentSize = getContentSerializedSize(block);
			overloadedBlocksSample.add(contentSize > maxBlockSize * overloadThreshold);
			underloadedBlocksSample.add(contentSize < maxBlockSize * underloadedThreshold);
			if (overloadedBlocksSample.size() > blocksSampleSize)
				overloadedBlocksSample.remove(0);
			if (underloadedBlocksSample.size() > blocksSampleSize)
				underloadedBlocksSample.remove(0);
		}
	}

	private int getContentSerializedSize(BlockmessBlock block) {
		return block.getContentList().getSerializedSize();
	}

	private void updateNextRank() {
		BlockmessBlock nextFinalized = finalizedBuffer.peek();
		if (nextFinalized != null && nextFinalized.getNextRank() > minNextRank)
			minNextRank = nextFinalized.getNextRank();
	}

	@Override
	public boolean shouldSpawn() {
		return isOverloaded();
	}

	private boolean isOverloaded() {
		return getNumOverloaded() > blocksSampleSize / 2;
	}

	@Override
	public int getNumOverloaded() {
		return getTrueSamples(overloadedBlocksSample);
	}

	@Override
	public int getNumFinalizedPending() {
		return finalizedBuffer.size();
	}

	private int getTrueSamples(List<Boolean> samples) {
		return (int) samples.stream().filter(s -> s).count();
	}

	@Override
	public boolean shouldMerge() {
		return false;
	}

	@Override
	public boolean isUnderloaded() {
		return getNumUnderloaded() > blocksSampleSize / 2;
	}

	@Override
	public long getMinimumRank() {
		return minRank;
	}

	@Override
	public Set<BlockmessBlock> getBlocks(Set<UUID> blockIds) {
		return blockIds.stream().map(blocks::get).filter(Objects::nonNull).collect(toSet());
	}

	@Override
	public int getNumUnderloaded() {
		return getTrueSamples(underloadedBlocksSample);
	}

	@Override
	public void resetSamples() {
		this.blocksBeforeResumingMetrics = getFinalizedWeight();
		this.overloadedBlocksSample.clear();
		this.underloadedBlocksSample.clear();
	}

	@Override
	public long getRankFromRefs(Set<UUID> refs) {
		OptionalLong nextRank = refs.stream()
				.map(blocks::get)
				.filter(Objects::nonNull)
				.mapToLong(BlockmessBlock::getNextRank)
				.max();
		return nextRank.orElse(minRank);
	}

	@Override
	public Set<BlockmessChain> getPriorityChains() {
		return emptySet();
	}

	@Override
	public void lowerLeafDepth() {
		depth--;
	}

	@Override
	public long getNextRank() {
		return minNextRank;
	}

	@Override
	public void spawnPermanentChildren(UUID lftId, UUID rgtId) {
		depth++;
		ParentTreeNode treeRoot = parent.getTreeRoot();
		ReferenceNode lft = new ReferenceNode(props, lftId, treeRoot,
				0, 1, depth, new ComposableOperationMapperImp());
		ReferenceNode rgt = new ReferenceNode(props, rgtId, treeRoot,
				0, 1, depth, new ComposableOperationMapperImp());
		PermanentChainNode encapsulating =
				new PermanentChainNode(this.parent, this, lft, rgt);
		parent.replaceChild(encapsulating);
		this.parent = encapsulating;
		resetSamples();
		parent.createChains(List.of(lft, rgt));
	}

	@Override
	public void submitContentDirectly(Collection<AppOperation> content) {
		operationMapper.submitOperations(content);
	}

	@Override
	public int countReferencedPermanent() {
		return 0;
	}

	@Override
	public void attachObserver(LedgerObserver observer) {
		try {
			observersLock.writeLock().lock();
			observers.add(observer);
		} finally {
			observersLock.writeLock().unlock();
		}
	}

	@Override
	public void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
		blocks.put(block.getBlockId(), block);
		logger.debug("Delivering non finalized block {} in Chain {}",
				block.getBlockId(), chainId);
		deliverNonFinalizedBlockToObservers(block, weight);
	}

	private void deliverNonFinalizedBlockToObservers(BlockmessBlock block, int weight) {
		try {
			observersLock.readLock().lock();
			for (var observer : observers)
				observer.deliverNonFinalizedBlock(block, weight);
		} finally {
			observersLock.readLock().unlock();
		}
	}

	@Override
	public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
		if (finalized.isEmpty() && discarded.isEmpty()) return;
		List<BlockmessBlock> finalizedBlocks = finalized.stream().map(blocks::get).collect(toList());
		finalizedBuffer.addAll(finalizedBlocks);
		updateNextRank();
		operationMapper.deleteOperations(getFinalizedContent(finalized));
		finalized.forEach(blocks::remove);
		logger.info("Delivering finalized blocks {} in Chain {}",
				finalized, chainId);
		logger.debug("Observed {} blocks over the size threshold",
				overloadedBlocksSample.stream().filter(b -> b).count());
		deliverFinalizedBlocksToObservers(finalized, discarded);
	}

	private void deliverFinalizedBlocksToObservers(List<UUID> finalized, Set<UUID> discarded) {
		try {
			observersLock.readLock().lock();
			for (var observer : observers)
				observer.deliverFinalizedBlocks(finalized, discarded);
		} finally {
			observersLock.readLock().unlock();
		}
	}

	private Set<UUID> getFinalizedContent(List<UUID> finalized) {
		return getTxsInBufferedFinalizedBlocks(
				finalized.stream()
						.map(blocks::get))
				.map(AppOperation::getId)
				.collect(toSet());
	}

	private Stream<AppOperation> getTxsInBufferedFinalizedBlocks(Stream<BlockmessBlock> stream) {
		return stream
				.map(BlockmessBlock::getContentList)
				.map(ContentList::getContentList)
				.flatMap(Collection::stream);
	}

	@Override
	public Collection<AppOperation> getStoredOperations() {
		return Stream.concat(
				operationMapper.getStoredOperations().stream(),
				getTxsInBufferedFinalizedBlocks(finalizedBuffer.stream())
		).collect(toSet());
	}

	private interface ExcludeInnerLedger {
		void attachObserver(LedgerObserver observer);
	}

	private interface ExcludeOperationMapper {
		Collection<AppOperation> getStoredOperations();
	}

}
