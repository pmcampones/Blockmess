package ledger.ledgerManager.nodes;

import catecoin.blockConstructors.ComposableContentStorage;
import catecoin.blockConstructors.ContentStorage;
import catecoin.blockConstructors.StructuredValueMask;
import catecoin.txs.Transaction;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import org.apache.commons.lang3.tuple.Pair;
import utils.CryptographicUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.SetUtils.union;

/**
 * Represents a node referencing Chains yet to be finalized.
 * <p>This node monitors the flux of blocks from the inner nodes
 * and communicates changes to the {@link ledger.ledgerManager.LedgerManager}.</p>
 */
public class TempChainNode implements InnerNode, LedgerObserver<BlockmessBlock>, BlockmessChain {

    private final Properties props;

    /**
     * Maps the identifier of the Chain root block to the Chains that are originated.
     * <p>Eventually, one and only one of the root blocks in this DS will originate a valid Chain.</p>
     */
    private final Map<UUID, Pair<ReferenceNode, ReferenceNode>> tentativeChains = new HashMap<>();
    private final Pair<ComposableContentStorage<Transaction>,
            ComposableContentStorage<Transaction>> contentStoragePair;
    private ParentTreeNode parent;

    private final int finalizedWeight;

    private final int rootWeight;

    /**
     * How deep is this Chain in the Blockmess Tree
     */
    private final int ChainDepth;
    private BlockmessChain inner;

    public TempChainNode(
            Properties props, BlockmessChain inner, ParentTreeNode parent,
            UUID ChainOriginatorBlockId, int ChainDepth,
            Pair<ComposableContentStorage<Transaction>, ComposableContentStorage<Transaction>> contentStoragePair)
            throws PrototypeHasNotBeenDefinedException {
        this.props = props;
        this.inner = inner;
        inner.attachObserver(this);
        this.parent = parent;
        this.finalizedWeight = parseInt(props.getProperty("finalizedWeight", "6"));
        this.rootWeight = inner.getWeight(ChainOriginatorBlockId);
        this.ChainDepth = ChainDepth;
        this.contentStoragePair = contentStoragePair;
        fillChainMap(ChainOriginatorBlockId);
    }

    private void fillChainMap(UUID ChainOriginatorBlockId) throws PrototypeHasNotBeenDefinedException {
        Set<UUID> rootIds = inner.getFollowing(ChainOriginatorBlockId, finalizedWeight + 3);
        Set<BlockmessBlock> roots = inner.getBlocks(rootIds);
        for (BlockmessBlock root : roots)
            tentativeChains.put(root.getBlockId(), computeChains(root));
        parent.createChains(getTentative());
    }

    private List<BlockmessChain> getTentative() {
        return tentativeChains.values().stream()
                .map(pair -> List.of(pair.getLeft(), pair.getRight()))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private Pair<ReferenceNode, ReferenceNode> computeChains(BlockmessBlock root)
            throws PrototypeHasNotBeenDefinedException {
        ParentTreeNode treeRoot = parent.getTreeRoot();
        UUID lftId = computeChainId(root.getBlockId(), "lft".getBytes());
        ReferenceNode lft = new ReferenceNode(props, lftId, treeRoot,
                root.getNextRank(), root.getNextRank(), ChainDepth, contentStoragePair.getLeft(), root.getBlockId());
        UUID rgtId = computeChainId(root.getBlockId(), "rgt".getBytes());
        ReferenceNode rgt = new ReferenceNode(props, rgtId, treeRoot,
                root.getNextRank(), root.getNextRank(), ChainDepth, contentStoragePair.getRight(), root.getBlockId());
        return Pair.of(lft, rgt);
    }

    private UUID computeChainId(UUID root, byte[] salt) {
        ByteBuffer buffer = ByteBuffer.allocate(2 * Long.BYTES + salt.length);
        buffer.putLong(root.getMostSignificantBits());
        buffer.putLong(root.getLeastSignificantBits());
        buffer.put(salt);
        return CryptographicUtils.generateUUIDFromBytes(buffer.array());
    }

    @Override
    public Set<UUID> getBlockR() {
        return inner.getBlockR();
    }

    @Override
    public void submitBlock(BlockmessBlock block) {
        inner.submitBlock(block);
    }

    @Override
    public void attachObserver(LedgerObserver<BlockmessBlock> observer) {
        inner.attachObserver(observer);
    }

    @Override
    public Set<UUID> getFollowing(UUID block, int distance) throws IllegalArgumentException {
        return inner.getFollowing(block, distance);
    }

    @Override
    public int getWeight(UUID block) throws IllegalArgumentException {
        return inner.getWeight(block);
    }

    @Override
    public boolean isInLongestChain(UUID nodeId) {
        return inner.isInLongestChain(nodeId);
    }

    @Override
    public void close() {
        inner.close();
    }

    @Override
    public UUID getChainId() {
        return inner.getChainId();
    }

    @Override
    public void replaceParent(ParentTreeNode parent) {
        this.parent = parent;
    }

    @Override
    public void spawnChildren(UUID originator) throws PrototypeHasNotBeenDefinedException {
        inner.spawnChildren(originator);
    }

    @Override
    public Set<UUID> mergeChildren() throws LedgerTreeNodeDoesNotExistException {
        if (!inner.isLeaf())
            return inner.mergeChildren();
        skipThisNode();
        inner.resetSamples();
        inner.lowerLeafDepth();
        inner.doubleChainThroughput();
        List<BlockmessChain> toMerge =  tentativeChains.values().stream()
                .map(p -> List.of(p.getLeft(), p.getRight()))
                .flatMap(Collection::stream)
                .collect(toList());
        toMerge.forEach(b -> inner.submitContent(b.getStoredContent()));
        return toMerge.stream().map(BlockmessChain::getChainId).collect(toSet());
    }

    private void skipThisNode() {
        parent.replaceChild(inner);
        inner.replaceParent(parent);
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public boolean hasFinalized() {
        return inner.hasFinalized();
    }

    @Override
    public BlockmessBlock peekFinalized() {
        return inner.peekFinalized();
    }

    @Override
    public BlockmessBlock deliverChainBlock() {
        BlockmessBlock delivered = inner.deliverChainBlock();
        Pair<ReferenceNode, ReferenceNode> confirmedChains =
                tentativeChains.get(delivered.getBlockId());
        if (confirmedChains != null) {
            replaceThisNode(confirmedChains);
            parent.forgetUnconfirmedChains(computeDiscardedChainsIds(confirmedChains));
        }
        return delivered;
    }

    private Set<UUID> computeDiscardedChainsIds(Pair<ReferenceNode, ReferenceNode> confirmed) {
        Set<UUID> confirmedIds = Set.of(confirmed.getLeft().getChainId(), confirmed.getRight().getChainId());
        return tentativeChains.values().stream()
                .map(p -> List.of(p.getLeft(), p.getRight()))
                .flatMap(Collection::stream)
                .map(BlockmessChain::getChainId)
                .filter(id -> !confirmedIds.contains(id))
                .collect(toSet());
    }

    @Override
    public boolean shouldSpawn() {
        return inner.shouldSpawn();
    }

    @Override
    public boolean shouldMerge() {
        if (inner.isLeaf())
            return inner.isUnderloaded()
                    && tentativeChains.values()
                    .stream()
                    .flatMap(p -> Stream.of(p.getLeft(), p.getRight()))
                    .allMatch(b -> b.isLeaf() && b.isUnderloaded());//tentativeChains.isEmpty();
        return inner.shouldMerge();
    }

    @Override
    public boolean isUnderloaded() {
        return inner.isUnderloaded();
    }

    @Override
    public long getMinimumRank() {
        return inner.getMinimumRank();
    }

    @Override
    public Set<BlockmessBlock> getBlocks(Set<UUID> blockIds) {
        return inner.getBlocks(blockIds);
    }

    @Override
    public void resetSamples() {
        inner.resetSamples();
    }

    @Override
    public long getRankFromRefs(Set<UUID> refs) {
        return inner.getRankFromRefs(refs);
    }

    @Override
    public Set<BlockmessChain> getPriorityChains() {
        var priorityChainsOpt = getPreferableTemp();
        if (priorityChainsOpt.isEmpty())
            return inner.getPriorityChains();
        var priorityChains = priorityChainsOpt.get();
        return union(inner.getPriorityChains(),
                union(priorityChains.getLeft().getPriorityChains(),
                        priorityChains.getRight().getPriorityChains()));
    }

    @Override
    public void lowerLeafDepth() {
        inner.lowerLeafDepth();
    }

    @Override
    public long getNextRank() {
        return inner.getNextRank();
    }

    @Override
    public void spawnPermanentChildren(UUID lftId, UUID rgtId)
            throws PrototypeHasNotBeenDefinedException {
        this.inner.spawnPermanentChildren(lftId, rgtId);
    }

    @Override
    public void submitContentDirectly(Collection<StructuredValue<Transaction>> content) {
        inner.submitContentDirectly(content);
    }

    @Override
    public int countReferencedPermanent() {
        return 2 + inner.countReferencedPermanent();
    }

    private Optional<Pair<ReferenceNode, ReferenceNode>> getPreferableTemp() {
        var eligible = tentativeChains.entrySet().stream()
                .filter(e -> this.isInLongestChain(e.getKey()))
                .map(Map.Entry::getValue)
                .collect(toList());
        if (eligible.isEmpty())
            return Optional.empty();
        return Optional.of(eligible.get(0));
    }

    private void replaceThisNode(Pair<ReferenceNode, ReferenceNode> correctChains) {
        PermanentChainNode replacement = new PermanentChainNode(this.parent, this.inner,
                correctChains.getLeft(), correctChains.getRight());
        this.parent.replaceChild(replacement);
        this.inner.replaceParent(replacement);
    }

    @Override
    public void forgetUnconfirmedChains(Set<UUID> discartedChainsIds) {
        parent.forgetUnconfirmedChains(discartedChainsIds);
    }

    @Override
    public void replaceChild(BlockmessChain newChild) {
        this.inner = newChild;
    }

    @Override
    public void createChains(List<BlockmessChain> createdChains) {
        parent.createChains(createdChains);
    }

    @Override
    public ParentTreeNode getTreeRoot() {
        return parent.getTreeRoot();
    }

    @Override
    public void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
        if (weight == rootWeight + finalizedWeight + 3)
            tryToInsertNewChainRoot(block);
    }

    @Override
    public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
        //Does nothing because any action taken by this class can only be done after the
        // ledger manager has linearized the blocks.
    }

    private void tryToInsertNewChainRoot(BlockmessBlock root) {
        try {
            Pair<ReferenceNode, ReferenceNode> createdChains = computeChains(root);
            tentativeChains.put(root.getBlockId(), createdChains);
            parent.createChains(List.of(createdChains.getLeft(), createdChains.getRight()));
        } catch (PrototypeHasNotBeenDefinedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Set<UUID> getForkBlocks(int depth) {
        return inner.getForkBlocks(depth);
    }

    @Override
    public int getNumUnderloaded() {
        return inner.getNumUnderloaded();
    }

    @Override
    public int getNumOverloaded() {
        return inner.getNumOverloaded();
    }

    @Override
    public int getFinalizedWeight() {
        return inner.getFinalizedWeight();
    }

    @Override
    public int getNumFinalizedPending() {
        return inner.getNumFinalizedPending();
    }

    @Override
    public List<StructuredValue<Transaction>> generateContentListList(Collection<UUID> states, int usedSpace)
            throws IOException {
        return inner.generateContentListList(states, usedSpace);
    }

    @Override
    public List<StructuredValue<Transaction>> generateBoundContentListList(Collection<UUID> states, int usedSpace, int maxTxs)
            throws IOException {
        return inner.generateBoundContentListList(states, usedSpace, maxTxs);
    }

    @Override
    public void submitContent(Collection<StructuredValue<Transaction>> content) {
        content.forEach(this::submitContent);
    }

    @Override
    public void submitContent(StructuredValue<Transaction> content) {
        StructuredValueMask.MaskResult res = content.matchIds();
        content.advanceMask();
        switch (res) {
            case LEFT:
                contentStoragePair.getLeft().submitContent(content);
                break;
            case RIGHT:
                contentStoragePair.getRight().submitContent(content);
                break;
            case CENTER:
                inner.submitContent(content);
        }
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        for (var pair : tentativeChains.values()) {
            pair.getLeft().deleteContent(contentIds);
            pair.getRight().deleteContent(contentIds);
        }
        inner.deleteContent(contentIds);
    }

    @Override
    public Collection<StructuredValue<Transaction>> getStoredContent() {
        return inner.getStoredContent();
    }

    @Override
    public void halveChainThroughput() {
        inner.halveChainThroughput();
    }

    @Override
    public void doubleChainThroughput() {
        inner.doubleChainThroughput();
    }

    @Override
    public int getThroughputReduction() {
        return inner.getThroughputReduction();
    }

    @Override
    public void setChainThroughputReduction(int reduction) {
        inner.setChainThroughputReduction(reduction);
    }

    @Override
    public Pair<ComposableContentStorage<Transaction>, ComposableContentStorage<Transaction>> separateContent(
            StructuredValueMask mask, ContentStorage<StructuredValue<Transaction>> innerLft,
            ContentStorage<StructuredValue<Transaction>> innerRgt) {
        return inner.separateContent(mask, innerLft, innerRgt);
    }

    @Override
    public void aggregateContent(Collection<ComposableContentStorage<Transaction>> composableBlockConstructors) {
        inner.aggregateContent(composableBlockConstructors);
    }
}
