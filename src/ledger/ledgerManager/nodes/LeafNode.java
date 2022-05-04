package ledger.ledgerManager.nodes;

import catecoin.blockConstructors.*;
import catecoin.txs.IndexableContent;
import ledger.DebugLedger;
import ledger.Ledger;
import ledger.LedgerObserver;
import ledger.blocks.BlockContent;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import ledger.prototype.LedgerPrototype;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.io.IOException;
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
 * (most likely a {@link ledger.blockchain.Blockchain})
 * and maintains a buffer of finalized blocks to aid the delivered block linearization process
 * undertook by the {@link ledger.ledgerManager.LedgerManager}.</p>
 */
public class LeafNode<E extends IndexableContent, C extends BlockContent<StructuredValue<E>>, P extends SybilElectionProof>
        implements DebugBlockmessChain<E,C,P>, LedgerObserver<BlockmessBlock<C,P>> {

    private static final Logger logger = LogManager.getLogger(LeafNode.class);

    private final Properties props;

    private final UUID ChainId;

    private final Ledger<BlockmessBlock<C,P>> ledger;

    private final List<LedgerObserver<BlockmessBlock<C,P>>> observers = new LinkedList<>();

    private final ReadWriteLock observersLock = new ReentrantReadWriteLock();

    private ParentTreeNode<E,C,P> parent;

    private final ComposableContentStorage<E> contentStorage;

    /**
     * Contains a mapping between all the blocks' ids and themselves.
     * <p>Used because the inner ledger does not provide the finalized blocks, only their identifier.
     * However the blocks pass through this object before being finalized.</p>
     * <p>Shouldn't need to be concurrent if the inner {@link Ledger} is a {@link ledger.blockchain.Blockchain},
     * however, in case the ledger implementation is changed, this will be kept as concurrent.</p>
     */
    private final Map<UUID, BlockmessBlock<C,P>> blocks = new ConcurrentHashMap<>();

    /**
     * Stores the finalized blocks on this Chain.
     * <p>Added as they are finalized in the ledger and removed when they are delivered to the application.</p>
     */
    private final Queue<BlockmessBlock<C,P>> finalizedBuffer = new ConcurrentLinkedQueue<>();

    /** Ledger<BlockmessBlock<C,P>> ledger
     * Number of samples used to determine if the Chain should spawn new Chains or merge into its parent.
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

    private long minNextRank;

    private int blocksBeforeResumingMetrics = 0;

    /**
     * How deep is this Chain in the Blockmess Tree
     */
    private int depth;

    public LeafNode(
            Properties props, UUID ChainId, ParentTreeNode<E,C,P> parent,
            long minRank, long minNextRank, int depth, ComposableContentStorage<E> contentStorage)
            throws PrototypeHasNotBeenDefinedException {
        this(props, ChainId, parent, minRank, minNextRank, depth, contentStorage, ChainId);
    }

    public LeafNode(
            Properties props, UUID ChainId, ParentTreeNode<E,C,P> parent,
            long minRank, long minNextRank, int depth, ComposableContentStorage<E> contentStorage, UUID prevBlock)
            throws PrototypeHasNotBeenDefinedException {
        this.props = props;
        this.ChainId = ChainId;
        this.ledger = LedgerPrototype.getLedgerCopy(prevBlock);
        ledger.attachObserver(this);
        this.parent = parent;
        this.overloadThreshold = parseFloat(props.getProperty("overloadThreshold", "0.9f"));
        this.underloadedThreshold = parseFloat(props.getProperty("underloadedThreshold", "0.4f"));
        this.blocksSampleSize = parseInt(props.getProperty("blockSampleSize", "15"));
        this.maxBlockSize = parseInt(props.getProperty("maxBlockSize", "21000"));
        this.minRank = minRank;
        this.minNextRank = minNextRank;
        this.depth = depth;
        this.contentStorage = contentStorage;
    }

    @Override
    public UUID getChainId() {
        return ChainId;
    }

    @Override
    public Set<UUID> getBlockR() {
        return ledger.getBlockR();
    }

    @Override
    public void submitBlock(BlockmessBlock<C,P> block) {
        ledger.submitBlock(block);
    }

    @Override
    public void attachObserver(LedgerObserver<BlockmessBlock<C, P>> observer) {
        try {
            observersLock.writeLock().lock();
            observers.add(observer);
        } finally {
            observersLock.writeLock().unlock();
        }
    }

    @Override
    public Set<UUID> getFollowing(UUID block, int distance) throws IllegalArgumentException {
        return ledger.getFollowing(block, distance);
    }

    @Override
    public int getWeight(UUID block) throws IllegalArgumentException {
        return ledger.getWeight(block);
    }

    @Override
    public boolean isInLongestChain(UUID nodeId) {
        return ledger.isInLongestChain(nodeId);
    }

    @Override
    public void close() {
        ledger.close();
    }

    @Override
    public void replaceParent(ParentTreeNode<E,C,P> parent) {
        this.parent = parent;
    }

    @Override
    public void spawnChildren(UUID originator) throws PrototypeHasNotBeenDefinedException {
        StructuredValueMask mask = new StructuredValueMask(depth);
        ContentStorage<StructuredValue<E>> lft = ContentStoragePrototype.getPrototype();
        ContentStorage<StructuredValue<E>> rgt = ContentStoragePrototype.getPrototype();
        depth++;
        contentStorage.halveChainThroughput();
        Pair<ComposableContentStorage<E>, ComposableContentStorage<E>> spawnedChainDirectors =
                contentStorage.separateContent(mask, lft, rgt);
        TempChainNode<E,C,P> encapsulating =
                new TempChainNode<>(props, this, parent, originator, depth, spawnedChainDirectors);
        parent.replaceChild(encapsulating);
        this.parent = encapsulating;
        resetSamples();
    }

    @Override
    public void spawnPermanentChildren(UUID lftId, UUID rgtId)
            throws PrototypeHasNotBeenDefinedException {
        depth++;
        contentStorage.halveChainThroughput();
        ParentTreeNode<E,C,P> treeRoot = parent.getTreeRoot();
        ReferenceNode<E,C,P> lft = new ReferenceNode<>(props, lftId, treeRoot,
                0, 1, depth, new ComposableContentStorageImp<>(),
                new UUID(0,0));
        ReferenceNode<E,C,P> rgt = new ReferenceNode<>(props, rgtId, treeRoot,
                0, 1, depth, new ComposableContentStorageImp<>(),
                new UUID(0,0));
        lft.setChainThroughputReduction(2 * contentStorage.getThroughputReduction());
        rgt.setChainThroughputReduction(2 * contentStorage.getThroughputReduction());
        PermanentChainNode<E,C,P> encapsulating =
                new PermanentChainNode<>(this.parent, this, lft, rgt);
        parent.replaceChild(encapsulating);
        this.parent = encapsulating;
        resetSamples();
        parent.createChains(List.of(lft, rgt));
    }

    @Override
    public void submitContentDirectly(Collection<StructuredValue<E>> content) {
        contentStorage.submitContent(content);
    }

    @Override
    public int countReferencedPermanent() {
        return 0;
    }

    @Override
    public Set<UUID> mergeChildren() throws LedgerTreeNodeDoesNotExistException {
        throw new LedgerTreeNodeDoesNotExistException(ChainId);
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public void deliverNonFinalizedBlock(BlockmessBlock<C, P> block, int weight) {
        blocks.put(block.getBlockId(), block);
        logger.debug("Delivering non finalized block {} in Chain {}",
                block.getBlockId(), ChainId);
        deliverNonFinalizedBlockToObservers(block, weight);
    }

    private void deliverNonFinalizedBlockToObservers(BlockmessBlock<C, P> block, int weight) {
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
        List<BlockmessBlock<C,P>> finalizedBlocks = finalized.stream().map(blocks::get).collect(toList());
        finalizedBuffer.addAll(finalizedBlocks);
        updateNextRank();
        contentStorage.deleteContent(getFinalizedContent(finalized));
        finalized.forEach(blocks::remove);
        logger.info("Delivering finalized blocks {} in Chain {}",
                finalized, ChainId);
        logger.debug("Observed {} blocks over the size threshold",
                overloadedBlocksSample.stream().filter(b -> b).count());
        deliverFinalizedBlocksToObservers(finalized, discarded);
    }

    private Set<UUID> getFinalizedContent(List<UUID> finalized) {
        return getTxsInBufferedFinalizedBlocks(
                finalized.stream()
                .map(blocks::get))
                .map(StructuredValue::getId)
                .collect(toSet());
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

    @Override
    public boolean hasFinalized() {
        return !finalizedBuffer.isEmpty();
    }

    @Override
    public BlockmessBlock<C,P> peekFinalized() {
        return finalizedBuffer.peek();
    }

    @Override
    public BlockmessBlock<C,P> deliverChainBlock() {
        BlockmessBlock<C,P> block = finalizedBuffer.poll();
        if (block != null) {
            computeBlockSizeStatistics(block);
            updateNextRank();
        }
        return block;
    }

    private void updateNextRank() {
        BlockmessBlock<C,P> nextFinalized = finalizedBuffer.peek();
        if (nextFinalized != null && nextFinalized.getNextRank() > minNextRank)
            minNextRank = nextFinalized.getNextRank();
    }

    private void computeBlockSizeStatistics(BlockmessBlock<C, P> block) {
        if (blocksBeforeResumingMetrics > 0)
            blocksBeforeResumingMetrics--;
        else {
            int contentSize = getContentSerializedSize(block);
            int proofSize = getProofSize(block);
            int headerSize = getBlockSerializedSize(block) - contentSize;
            int discountedMaxBlockSize = maxBlockSize - proofSize - headerSize;
            overloadedBlocksSample.add(contentSize > discountedMaxBlockSize * overloadThreshold);
            underloadedBlocksSample.add(contentSize < discountedMaxBlockSize * underloadedThreshold);
            System.out.println(block.getBlockContent().getContentList().size() + " : " + (contentSize < discountedMaxBlockSize * underloadedThreshold));
            if (overloadedBlocksSample.size() > blocksSampleSize)
                overloadedBlocksSample.remove(0);
            if (underloadedBlocksSample.size() > blocksSampleSize)
                underloadedBlocksSample.remove(0);
        }
    }

    private int getContentSerializedSize(BlockmessBlock<C,P> block) {
        try {
            return block.getBlockContent().getSerializedSize();
        } catch (IOException e) {
            e.printStackTrace();
            throw new Error();
        }
    }

    private int getBlockSerializedSize(BlockmessBlock<C,P> block) {
        try {
            return block.getSerializedSize();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return maxBlockSize / 2;
    }

    private int getProofSize(BlockmessBlock<C,P> block) {
        try {
            return block.getSybilElectionProof().getSerializedSize();
        } catch (IOException e) {
            e.printStackTrace();
            throw new Error();
        }
    }

    @Override
    public boolean shouldSpawn() {
        return isOverloaded();
    }

    @Override
    public boolean isOverloaded() {
        return getNumOverloaded() > blocksSampleSize / 2;
    }

    @Override
    public int getMaxBlockSize() {
        return maxBlockSize;
    }

    @Override
    public boolean hasTemporaryChains() {
        return false;
    }

    @Override
    public int getNumChaining() {
        return 0;
    }

    @Override
    public int getNumSpawnedChains() {
        return 0;
    }

    @Override
    public List<DebugBlockmessChain<E, C, P>> getSpawnedChains() {
        return Collections.emptyList();
    }

    @Override
    public int getNumFinalizedPending() {
        return finalizedBuffer.size();
    }

    @Override
    public int getNumOverloaded() {
        return getTrueSamples(overloadedBlocksSample);
    }

    @Override
    public int getFinalizedWeight() {
        return ((DebugLedger<BlockmessBlock<C,P>>)ledger).getFinalizedWeight();
    }

    @Override
    public Set<UUID> getFinalizedIds() {
        return ((DebugLedger<BlockmessBlock<C,P>>)ledger).getFinalizedIds();
    }

    @Override
    public Set<UUID> getNodesIds() {
        return ((DebugLedger<BlockmessBlock<C,P>>)ledger).getNodesIds();
    }

    @Override
    public Set<UUID> getForkBlocks(int depth) {
        return ((DebugLedger<BlockmessBlock<C,P>>)ledger).getForkBlocks(depth);
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
    public Set<BlockmessBlock<C, P>> getBlocks(Set<UUID> blockIds) {
        return blockIds.stream().map(blocks::get).filter(Objects::nonNull).collect(toSet());
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
    public Set<BlockmessChain<E, C, P>> getPriorityChains() {
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
    public int getNumSamples() {
        return blocksSampleSize;
    }

    @Override
    public int getNumUnderloaded() {
        return getTrueSamples(underloadedBlocksSample);
    }

    private int getTrueSamples(List<Boolean> samples) {
        return (int) samples.stream().filter(s -> s).count();
    }

    @Override
    public List<StructuredValue<E>> generateBlockContentList(Collection<UUID> states, int usedSpace)
            throws IOException {
        return contentStorage.generateBlockContentList(states, usedSpace);
    }

    @Override
    public List<StructuredValue<E>> generateBoundBlockContentList(Collection<UUID> states, int usedSpace, int maxTxs)
            throws IOException {
        return contentStorage.generateBoundBlockContentList(states, usedSpace, maxTxs);
    }

    @Override
    public void submitContent(Collection<StructuredValue<E>> content) {
        contentStorage.submitContent(content);
    }

    @Override
    public void submitContent(StructuredValue<E> content) {
        contentStorage.submitContent(content);
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        contentStorage.deleteContent(contentIds);
    }

    @Override
    public Collection<StructuredValue<E>> getStoredContent() {
        return Stream.concat(
                contentStorage.getStoredContent().stream(),
                getTxsInBufferedFinalizedBlocks(finalizedBuffer.stream())
                ).collect(toSet());
    }

    private Stream<StructuredValue<E>> getTxsInBufferedFinalizedBlocks(Stream<BlockmessBlock<C, P>> stream) {
        return stream
                .map(BlockmessBlock::getBlockContent)
                .map(BlockContent::getContentList)
                .flatMap(Collection::stream);
    }

    @Override
    public void halveChainThroughput() {
        contentStorage.halveChainThroughput();
    }

    @Override
    public void doubleChainThroughput() {
        contentStorage.doubleChainThroughput();
    }

    @Override
    public int getThroughputReduction() {
        return contentStorage.getThroughputReduction();
    }

    @Override
    public void setChainThroughputReduction(int reduction) {
        contentStorage.setChainThroughputReduction(reduction);
    }

    @Override
    public Pair<ComposableContentStorage<E>, ComposableContentStorage<E>> separateContent(
            StructuredValueMask mask,
            ContentStorage<StructuredValue<E>> innerLft,
            ContentStorage<StructuredValue<E>> innerRgt) {
        return contentStorage.separateContent(mask, innerLft, innerRgt);
    }

    @Override
    public void aggregateContent(Collection<ComposableContentStorage<E>> blockConstructors) {
        contentStorage.aggregateContent(blockConstructors);
    }

}
