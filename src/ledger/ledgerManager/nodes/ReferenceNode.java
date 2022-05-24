package ledger.ledgerManager.nodes;

import cmux.AppOperation;
import cmux.CMuxMask;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import operationMapper.ComposableOperationMapper;
import operationMapper.OperationMapper;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.*;

import static org.apache.commons.collections4.SetUtils.union;

/**
 * This object serves as an entrypoint for the {@link ledger.ledgerManager.LedgerManager}
 * to directly contact the Chain.
 * <p>All operations concerning the Chain are executed by the inner implementations of the {@link BlockmessChain}.</p>
 */
public class ReferenceNode implements InnerNode, BlockmessChain{

    /**
     * Reference to the lead node in this Chain.
     * <p>This reference reduces the chain of method calls for operations that always end up calling the leaf.</p>
     */
    private final LeafNode leaf;
    /**
     * Uses the State design pattern to modify its behaviour
     * depending on whether it is a leaf node or an inner node.
     */
    private BlockmessChain nodeState;
    /**
     * References the inner node from a parent Chain that spawned this.
     * <p>It could also be a reference to the LedgerManager itself.</p>
     */
    private ParentTreeNode parent;

    public ReferenceNode(
            Properties props, UUID chainId, ParentTreeNode parent,
            long minRank, long minNextRank, int depth, ComposableOperationMapper contentStorage) {
        this.leaf = new LeafNode(props, chainId, this, minRank, minNextRank, depth, contentStorage);
        this.nodeState = leaf;
        this.parent = parent;
    }

    public ReferenceNode(
            Properties props, UUID chainId, ParentTreeNode parent,
            long minRank, long minNextRank, int depth, ComposableOperationMapper contentStorage, UUID prevBlock) {
        this.leaf = new LeafNode(props, chainId, this, minRank, minNextRank, depth, contentStorage, prevBlock);
        this.nodeState = leaf;
        this.parent = parent;
    }

    @Override
    public void spawnChildren(UUID originator) {
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
    public void submitBlock(BlockmessBlock block) {
        leaf.submitBlock(block);
    }

    @Override
    public void attachObserver(LedgerObserver observer) {
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
    public void replaceParent(ParentTreeNode parent) {
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
    public BlockmessBlock peekFinalized() {
        return leaf.peekFinalized();
    }

    /*
     *  Must not send directly to the leaf node
     */
    @Override
    public BlockmessBlock deliverChainBlock() {
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
    public Set<BlockmessBlock> getBlocks(Set<UUID> blockIds) {
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
    public Set<BlockmessChain> getPriorityChains() {
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
    public void spawnPermanentChildren(UUID lftId, UUID rgtId) {
        nodeState.spawnPermanentChildren(lftId, rgtId);
    }

    @Override
    public void submitContentDirectly(Collection<AppOperation> content) {
        leaf.submitContentDirectly(content);
    }

    @Override
    public int countReferencedPermanent() {
        return nodeState.countReferencedPermanent();
    }

    @Override
    public void replaceChild(BlockmessChain newChild) {
        this.nodeState = newChild;
    }

    @Override
    public void forgetUnconfirmedChains(Set<UUID> discartedChainsIds) {
        parent.forgetUnconfirmedChains(discartedChainsIds);
    }

    @Override
    public void createChains(List<BlockmessChain> createdChains) {
        parent.createChains(createdChains);
    }

    @Override
    public ParentTreeNode getTreeRoot() {
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
    public List<AppOperation> generateContentList(Collection<UUID> states, int usedSpace)
            throws IOException {
        return leaf.generateContentList(states, usedSpace);
    }

    @Override
    public void submitContent(Collection<AppOperation> content) {
        nodeState.submitContent(content);
    }

    @Override
    public void submitContent(AppOperation content) {
        nodeState.submitContent(content);
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        nodeState.deleteContent(contentIds);
    }

    @Override
    public Collection<AppOperation> getStoredContent() {
        return leaf.getStoredContent();
    }

    @Override
    public Pair<ComposableOperationMapper, ComposableOperationMapper> separateContent(
            CMuxMask mask, OperationMapper innerLft, OperationMapper innerRgt) {
        return leaf.separateContent(mask, innerLft, innerRgt);
    }

    @Override
    public void aggregateContent(Collection<ComposableOperationMapper> blockConstructors) {
        leaf.aggregateContent(blockConstructors);
    }

}
