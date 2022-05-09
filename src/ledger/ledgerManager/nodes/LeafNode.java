package ledger.ledgerManager.nodes;

import blockConstructors.*;
import ledger.AppContent;
import ledger.Ledger;
import ledger.LedgerObserver;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.ContentList;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
public class LeafNode implements BlockmessChain, LedgerObserver {

    private static final Logger logger = LogManager.getLogger(LeafNode.class);

    private final Properties props;

    private final UUID chainId;

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

    private final ComposableContentStorage contentStorage;
    /**
     * Stores the finalized blocks on this Chain.
     * <p>Added as they are finalized in the ledger and removed when they are delivered to the application.</p>
     */
    private final Queue<BlockmessBlock> finalizedBuffer = new ConcurrentLinkedQueue<>();
    private ParentTreeNode parent;

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
            Properties props, UUID ChainId, ParentTreeNode parent,
            long minRank, long minNextRank, int depth, ComposableContentStorage contentStorage) {
        this(props, ChainId, parent, minRank, minNextRank, depth, contentStorage, ChainId);
    }

    public LeafNode(
            Properties props, UUID chainId, ParentTreeNode parent,
            long minRank, long minNextRank, int depth, ComposableContentStorage contentStorage, UUID prevBlock) {
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
        this.contentStorage = contentStorage;
    }

    @Override
    public UUID getChainId() {
        return chainId;
    }

    @Override
    public Set<UUID> getBlockR() {
        return ledger.getBlockR();
    }

    @Override
    public void submitBlock(BlockmessBlock block) {
        ledger.submitBlock(block);
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
    public void replaceParent(ParentTreeNode parent) {
        this.parent = parent;
    }

    @Override
    public void spawnChildren(UUID originator) {
        CMuxMask mask = new CMuxMask(depth);
        ContentStorage lft = new BaseContentStorage();
        ContentStorage rgt = new BaseContentStorage();
        depth++;
        Pair<ComposableContentStorage, ComposableContentStorage> spawnedChainDirectors =
                contentStorage.separateContent(mask, lft, rgt);
        TempChainNode encapsulating =
                new TempChainNode(props, this, parent, originator, depth, spawnedChainDirectors);
        parent.replaceChild(encapsulating);
        this.parent = encapsulating;
        resetSamples();
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

    @Override
    public int countReferencedPermanent() {
        return 0;
    }

    @Override
    public Set<UUID> mergeChildren() throws LedgerTreeNodeDoesNotExistException {
        throw new LedgerTreeNodeDoesNotExistException(chainId);
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    private void computeBlockSizeStatistics(BlockmessBlock block) {
        if (blocksBeforeResumingMetrics > 0)
            blocksBeforeResumingMetrics--;
        else {
            int contentSize = getContentSerializedSize(block);
            int proofSize = getProofSize(block);
            int headerSize = getBlockSerializedSize(block) - contentSize;
            int discountedMaxBlockSize = maxBlockSize - proofSize - headerSize;
            overloadedBlocksSample.add(contentSize > discountedMaxBlockSize * overloadThreshold);
            underloadedBlocksSample.add(contentSize < discountedMaxBlockSize * underloadedThreshold);
            System.out.println(block.getContentList().getContentList().size() + " : " + (contentSize < discountedMaxBlockSize * underloadedThreshold));
            if (overloadedBlocksSample.size() > blocksSampleSize)
                overloadedBlocksSample.remove(0);
            if (underloadedBlocksSample.size() > blocksSampleSize)
                underloadedBlocksSample.remove(0);
        }
    }

    private int getContentSerializedSize(BlockmessBlock block) {
        try {
            return block.getContentList().getSerializedSize();
        } catch (IOException e) {
            e.printStackTrace();
            throw new Error();
        }
    }

    private int getBlockSerializedSize(BlockmessBlock block) {
        try {
            return block.getSerializedSize();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return maxBlockSize / 2;
    }

    private Set<UUID> getFinalizedContent(List<UUID> finalized) {
        return getTxsInBufferedFinalizedBlocks(
                finalized.stream()
                .map(blocks::get))
                .map(AppContent::getId)
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

    private int getProofSize(BlockmessBlock block) {
        try {
            return block.getSybilElectionProof().getSerializedSize();
        } catch (IOException e) {
            e.printStackTrace();
            throw new Error();
        }
    }

    @Override
    public Set<BlockmessBlock> getBlocks(Set<UUID> blockIds) {
        return blockIds.stream().map(blocks::get).filter(Objects::nonNull).collect(toSet());
    }

    @Override
    public Set<BlockmessChain> getPriorityChains() {
        return emptySet();
    }

    @Override
    public void spawnPermanentChildren(UUID lftId, UUID rgtId) {
        depth++;
        ParentTreeNode treeRoot = parent.getTreeRoot();
        ReferenceNode lft = new ReferenceNode(props, lftId, treeRoot,
                0, 1, depth, new ComposableContentStorageImp(),
                new UUID(0,0));
        ReferenceNode rgt = new ReferenceNode(props, rgtId, treeRoot,
                0, 1, depth, new ComposableContentStorageImp(),
                new UUID(0,0));
        PermanentChainNode encapsulating =
                new PermanentChainNode(this.parent, this, lft, rgt);
        parent.replaceChild(encapsulating);
        this.parent = encapsulating;
        resetSamples();
        parent.createChains(List.of(lft, rgt));
    }

    private Stream<AppContent> getTxsInBufferedFinalizedBlocks(Stream<BlockmessBlock> stream) {
        return stream
                .map(BlockmessBlock::getContentList)
                .map(ContentList::getContentList)
                .flatMap(Collection::stream);
    }

    private void updateNextRank() {
        BlockmessBlock nextFinalized = finalizedBuffer.peek();
        if (nextFinalized != null && nextFinalized.getNextRank() > minNextRank)
            minNextRank = nextFinalized.getNextRank();
    }

    @Override
    public void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
        blocks.put(block.getBlockId(), block);
        logger.debug("Delivering non finalized block {} in Chain {}",
                block.getBlockId(), chainId);
        deliverNonFinalizedBlockToObservers(block, weight);
    }

    @Override
    public boolean shouldSpawn() {
        return isOverloaded();
    }

    private boolean isOverloaded() {
        return getNumOverloaded() > blocksSampleSize / 2;
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
        return ledger.getFinalizedWeight();
    }

    @Override
    public Set<UUID> getForkBlocks(int depth) {
        return ledger.getForkBlocks(depth);
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
    public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
        if (finalized.isEmpty() && discarded.isEmpty()) return;
        List<BlockmessBlock> finalizedBlocks = finalized.stream().map(blocks::get).collect(toList());
        finalizedBuffer.addAll(finalizedBlocks);
        updateNextRank();
        contentStorage.deleteContent(getFinalizedContent(finalized));
        finalized.forEach(blocks::remove);
        logger.info("Delivering finalized blocks {} in Chain {}",
                finalized, chainId);
        logger.debug("Observed {} blocks over the size threshold",
                overloadedBlocksSample.stream().filter(b -> b).count());
        deliverFinalizedBlocksToObservers(finalized, discarded);
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
    public int getNumUnderloaded() {
        return getTrueSamples(underloadedBlocksSample);
    }

    private int getTrueSamples(List<Boolean> samples) {
        return (int) samples.stream().filter(s -> s).count();
    }

    @Override
    public void submitContentDirectly(Collection<AppContent> content) {
        contentStorage.submitContent(content);
    }

    @Override
    public List<AppContent> generateContentListList(Collection<UUID> states, int usedSpace)
            throws IOException {
        return contentStorage.generateContentListList(states, usedSpace);
    }

    @Override
    public void submitContent(Collection<AppContent> content) {
        contentStorage.submitContent(content);
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        contentStorage.deleteContent(contentIds);
    }

    @Override
    public void submitContent(AppContent content) {
        contentStorage.submitContent(content);
    }

    @Override
    public Collection<AppContent> getStoredContent() {
        return Stream.concat(
                contentStorage.getStoredContent().stream(),
                getTxsInBufferedFinalizedBlocks(finalizedBuffer.stream())
                ).collect(toSet());
    }

    @Override
    public Pair<ComposableContentStorage, ComposableContentStorage> separateContent(
            CMuxMask mask,
            ContentStorage innerLft,
            ContentStorage innerRgt) {
        return contentStorage.separateContent(mask, innerLft, innerRgt);
    }

    @Override
    public void aggregateContent(Collection<ComposableContentStorage> blockConstructors) {
        contentStorage.aggregateContent(blockConstructors);
    }

}
