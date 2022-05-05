package ledger.ledgerManager.nodes;

import catecoin.blockConstructors.ComposableContentStorage;
import catecoin.blockConstructors.ContentStorage;
import catecoin.blockConstructors.StructuredValueMask;
import catecoin.blocks.ContentList;
import catecoin.txs.IndexableContent;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import org.apache.commons.lang3.tuple.Pair;
import sybilResistantElection.SybilElectionProof;

import java.io.IOException;
import java.util.*;

import static org.apache.commons.collections4.SetUtils.union;

/**
 * This object serves as an entrypoint for the {@link ledger.ledgerManager.LedgerManager}
 * to directly contact the Chain.
 * <p>All operations concerning the Chain are executed by the inner implementations of the {@link BlockmessChain}.</p>
 */
public class ReferenceNode<E extends IndexableContent, C extends ContentList<StructuredValue<E>>, P extends SybilElectionProof>
        implements InnerNode<E,C,P>, BlockmessChain<E,C,P> {

    /**
     * Uses the State design pattern to modify its behaviour
     * depending on whether it is a leaf node or an inner node.
     */
    private BlockmessChain<E,C,P> nodeState;

    /**
     * Reference to the lead node in this Chain.
     * <p>This reference reduces the chain of method calls for operations that always end up calling the leaf.</p>
     */
    private final LeafNode<E,C,P> leaf;

    /**
     * References the inner node from a parent Chain that spawned this.
     * <p>It could also be a reference to the LedgerManager itself.</p>
     */
    private ParentTreeNode<E,C,P> parent;

    public ReferenceNode(
            Properties props, UUID ChainId, ParentTreeNode<E,C,P> parent,
            long minRank, long minNextRank, int depth, ComposableContentStorage<E> contentStorage)
            throws PrototypeHasNotBeenDefinedException {
        this.leaf = new LeafNode<>(props, ChainId, this, minRank, minNextRank, depth, contentStorage);
        this.nodeState = leaf;
        this.parent = parent;
    }

    public ReferenceNode(
            Properties props, UUID ChainId, ParentTreeNode<E,C,P> parent,
            long minRank, long minNextRank, int depth, ComposableContentStorage<E> contentStorage, UUID prevBlock)
            throws PrototypeHasNotBeenDefinedException {
        this.leaf = new LeafNode<>(props, ChainId, this, minRank, minNextRank, depth, contentStorage, prevBlock);
        this.nodeState = leaf;
        this.parent = parent;
    }

    @Override
    public void spawnChildren(UUID originator) throws PrototypeHasNotBeenDefinedException {
        nodeState.spawnChildren(originator);
    }

    @Override
    public Set<UUID> mergeChildren() throws LedgerTreeNodeDoesNotExistException {
        return nodeState.mergeChildren();
    }

    @Override
    public UUID getChainId() {
        return leaf.getChainId();
    }

    @Override
    public Set<UUID> getBlockR() {
        return leaf.getBlockR();
    }

    @Override
    public void submitBlock(BlockmessBlock<C,P> block) {
        leaf.submitBlock(block);
    }

    @Override
    public void attachObserver(LedgerObserver<BlockmessBlock<C,P>> observer) {
        leaf.attachObserver(observer);
    }

    @Override
    public Set<UUID> getFollowing(UUID block, int distance) throws IllegalArgumentException {
        return leaf.getFollowing(block, distance);
    }

    @Override
    public int getWeight(UUID block) throws IllegalArgumentException {
        return leaf.getWeight(block);
    }

    @Override
    public boolean isInLongestChain(UUID nodeId) {
        return leaf.isInLongestChain(nodeId);
    }

    @Override
    public void close() {
        leaf.close();
    }
    @Override
    public void replaceParent(ParentTreeNode<E,C,P> parent) {
        this.parent = parent;
    }

    @Override
    public boolean isLeaf() {
        return nodeState.isLeaf();
    }

    @Override
    public boolean hasFinalized() {
        return leaf.hasFinalized();
    }

    @Override
    public BlockmessBlock<C, P> peekFinalized() {
        return leaf.peekFinalized();
    }

    /*
     *  Must not send directly to the leaf node
     */
    @Override
    public BlockmessBlock<C, P> deliverChainBlock() {
        return nodeState.deliverChainBlock();
    }

    @Override
    public boolean shouldSpawn() {
        return leaf.shouldSpawn();
    }

    @Override
    public boolean shouldMerge() {
        return nodeState.shouldMerge();
    }

    @Override
    public boolean isUnderloaded() {
        return leaf.isUnderloaded();
    }

    @Override
    public long getMinimumRank() {
        return leaf.getMinimumRank();
    }

    @Override
    public Set<BlockmessBlock<C, P>> getBlocks(Set<UUID> blockIds) {
        return leaf.getBlocks(blockIds);
    }

    @Override
    public void resetSamples() {
        leaf.resetSamples();
    }

    @Override
    public long getRankFromRefs(Set<UUID> refs) {
        return leaf.getRankFromRefs(refs);
    }

    @Override
    public Set<BlockmessChain<E, C, P>> getPriorityChains() {
        return union(Set.of(this), nodeState.getPriorityChains());
    }

    @Override
    public void lowerLeafDepth() {
        leaf.lowerLeafDepth();
    }

    @Override
    public long getNextRank() {
        return leaf.getNextRank();
    }

    @Override
    public void spawnPermanentChildren(UUID lftId, UUID rgtId)
            throws PrototypeHasNotBeenDefinedException {
        nodeState.spawnPermanentChildren(lftId, rgtId);
    }

    @Override
    public void submitContentDirectly(Collection<StructuredValue<E>> content) {
        leaf.submitContentDirectly(content);
    }

    @Override
    public int countReferencedPermanent() {
        return nodeState.countReferencedPermanent();
    }

    @Override
    public void replaceChild(BlockmessChain<E,C,P> newChild) {
        this.nodeState = newChild;
    }

    @Override
    public void forgetUnconfirmedChains(Set<UUID> discartedChainsIds) {
        parent.forgetUnconfirmedChains(discartedChainsIds);
    }

    @Override
    public void createChains(List<BlockmessChain<E,C,P>> createdChains) {
        parent.createChains(createdChains);
    }

    @Override
    public ParentTreeNode<E,C,P> getTreeRoot() {
        return parent;
    }

    @Override
    public int getNumUnderloaded() {
        return leaf.getNumUnderloaded();
    }

    @Override
    public int getNumOverloaded() {
        return leaf.getNumOverloaded();
    }

    @Override
    public int getFinalizedWeight() {
        return leaf.getFinalizedWeight();
    }

    @Override
    public int getNumFinalizedPending() {
        return leaf.getNumFinalizedPending();
    }

    @Override
    public Set<UUID> getForkBlocks(int depth) {
        return leaf.getForkBlocks(depth);
    }

    @Override
    public List<StructuredValue<E>> generateContentListList(Collection<UUID> states, int usedSpace)
            throws IOException {
        return leaf.generateContentListList(states, usedSpace);
    }

    @Override
    public List<StructuredValue<E>> generateBoundContentListList(Collection<UUID> states, int usedSpace, int maxTxs)
            throws IOException {
        return leaf.generateBoundContentListList(states, usedSpace, maxTxs);
    }

    @Override
    public void submitContent(Collection<StructuredValue<E>> content) {
        nodeState.submitContent(content);
    }

    @Override
    public void submitContent(StructuredValue<E> content) {
        nodeState.submitContent(content);
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        nodeState.deleteContent(contentIds);
    }

    @Override
    public Collection<StructuredValue<E>> getStoredContent() {
        return leaf.getStoredContent();
    }

    @Override
    public void halveChainThroughput() {
        nodeState.halveChainThroughput();
    }

    @Override
    public void doubleChainThroughput() {
        nodeState.doubleChainThroughput();
    }

    @Override
    public int getThroughputReduction() {
        return leaf.getThroughputReduction();
    }

    @Override
    public void setChainThroughputReduction(int reduction) {
        leaf.setChainThroughputReduction(reduction);
    }

    @Override
    public Pair<ComposableContentStorage<E>, ComposableContentStorage<E>> separateContent(
            StructuredValueMask mask, ContentStorage<StructuredValue<E>> innerLft,
            ContentStorage<StructuredValue<E>> innerRgt) {
        return leaf.separateContent(mask, innerLft, innerRgt);
    }

    @Override
    public void aggregateContent(Collection<ComposableContentStorage<E>> blockConstructors) {
        leaf.aggregateContent(blockConstructors);
    }

}
