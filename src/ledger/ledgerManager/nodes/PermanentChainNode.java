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
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.apache.commons.collections4.SetUtils.union;

/**
 * Represents a Chaining of this Chain.
 * Is able to connect with two child Chains
 * and another node lower in the hierarchy within this Chain.
 * <p>The lower the InnerNode in the Chain's hierarchy,
 * the later were the referenced Chains spawned.</p>
 */
public class PermanentChainNode implements InnerNode, BlockmessChain{

    private final ReferenceNode lft, rgt;
    private ParentTreeNode parent;
    private BlockmessChain inner;

    public PermanentChainNode(ParentTreeNode parent, BlockmessChain inner,
                               ReferenceNode lft, ReferenceNode rgt) {
        this.parent = parent;
        this.inner = inner;
        this.lft = lft;
        this.rgt = rgt;
    }

    @Override
    public UUID getChainId() {
        return inner.getChainId();
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
    public void attachObserver(LedgerObserver observer) {
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
    public void replaceParent(ParentTreeNode parent) {
        this.parent = parent;
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
        return inner.deliverChainBlock();
    }

    @Override
    public boolean shouldSpawn() {
        return inner.shouldSpawn();
    }

    @Override
    public boolean shouldMerge() {
        if (inner.isLeaf())
            return inner.isUnderloaded()
                    && lft.isLeaf() && lft.isUnderloaded()
                    && rgt.isLeaf() && rgt.isUnderloaded();
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
        return union(inner.getPriorityChains(), union(lft.getPriorityChains(), rgt.getPriorityChains()));
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
    public void spawnChildren(UUID originator) {
        inner.spawnChildren(originator);
    }

    @Override
    public void submitContentDirectly(Collection<AppOperation> content) {
        inner.submitContentDirectly(content);
    }

    @Override
    public int countReferencedPermanent() {
        return 2 + inner.countReferencedPermanent();
    }

    @Override
    public void replaceChild(BlockmessChain newChild) {
        inner = newChild;
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
        return parent.getTreeRoot();
    }

    @Override
    public void spawnPermanentChildren(UUID lftId, UUID rgtId) {
        this.inner.spawnPermanentChildren(lftId, rgtId);
    }

    @Override
    public Set<UUID> mergeChildren() throws LedgerTreeNodeDoesNotExistException {
        if (inner.isLeaf()) {
            skipThisNode();
            //inner.resetSamples();
            inner.lowerLeafDepth();
            inner.submitOperations(lft.getStoredOperations());
            inner.submitOperations(rgt.getStoredOperations());
            return Set.of(lft.getChainId(), rgt.getChainId());
        } else return inner.mergeChildren();
    }

    /**
     * Merges two Chains in the Blockmess by skipping this node containing them.
     * <p>We connect this node's parent to this node's child, and vice-versa.
     * This ensures that the node cannot be accessed and thus will be removed.</p>
     * <p>One last reference resides in the direct connection between the {@link ledger.ledgerManager.LedgerManager}
     * and the {@link BlockmessChain}es, to remove it we notify the {@link ReferenceNode}s that
     * they are to be removed.</p>
     */
    private void skipThisNode() {
        parent.replaceChild(inner);
        inner.replaceParent(parent);
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
    public List<AppOperation> generateOperationList(Collection<UUID> states, int usedSpace)
            throws IOException {
        return inner.generateOperationList(states, usedSpace);
    }

    @Override
    public void submitOperations(Collection<AppOperation> operations) {
        operations.forEach(this::submitOperation);
    }

    @Override
    public void submitOperation(AppOperation operation) {
        CMuxMask.MaskResult res = operation.matchIds();
        operation.advanceMask();
        switch (res) {
            case LEFT:
                lft.submitOperation(operation);
                break;
            case RIGHT:
                rgt.submitOperation(operation);
                break;
            case CENTER:
                inner.submitOperation(operation);
        }
    }

    @Override
    public void deleteOperations(Set<UUID> operatationIds) {
        lft.deleteOperations(operatationIds);
        rgt.deleteOperations(operatationIds);
        inner.deleteOperations(operatationIds);
    }

    @Override
    public Collection<AppOperation> getStoredOperations() {
        return inner.getStoredOperations();
    }

    @Override
    public Pair<ComposableOperationMapper, ComposableOperationMapper> separateOperations(
            CMuxMask mask, OperationMapper innerLft, OperationMapper innerRgt) {
        return inner.separateOperations(mask, innerLft, innerRgt);
    }

    @Override
    public void aggregateOperations(Collection<ComposableOperationMapper> operationMappers) {
        inner.aggregateOperations(operationMappers);
    }
}
