package ledger.ledgerManager.nodes;

import catecoin.blockConstructors.ComposableContentStorage;
import catecoin.blockConstructors.ContentStorage;
import catecoin.blockConstructors.StructuredValueMask;
import catecoin.txs.IndexableContent;
import ledger.LedgerObserver;
import ledger.blocks.BlockContent;
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
 * Represents a Chaining of this Chain.
 * Is able to connect with two child Chains
 * and another node lower in the hierarchy within this Chain.
 * <p>The lower the InnerNode in the Chain's hierarchy,
 * the later were the referenced Chains spawned.</p>
 */
public class PermanentChainNode<E extends IndexableContent, C extends BlockContent<StructuredValue<E>>, P extends SybilElectionProof>
        implements InnerNode<E,C,P>, DebugBlockmessChain<E,C,P>{

    private ParentTreeNode<E,C,P> parent;

    private BlockmessChain<E,C,P> inner;

    private final ReferenceNode<E,C,P> lft, rgt;

    public PermanentChainNode(ParentTreeNode<E,C,P> parent, BlockmessChain<E,C,P> inner,
                               ReferenceNode<E,C,P> lft, ReferenceNode<E,C,P> rgt) {
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
    public void submitBlock(BlockmessBlock<C,P> block) {
        inner.submitBlock(block);
    }

    @Override
    public void attachObserver(LedgerObserver<BlockmessBlock<C, P>> observer) {
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
    public void replaceParent(ParentTreeNode<E,C,P> parent) {
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
    public BlockmessBlock<C, P> peekFinalized() {
        return inner.peekFinalized();
    }

    @Override
    public BlockmessBlock<C, P> deliverChainBlock() {
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
    public Set<BlockmessBlock<C, P>> getBlocks(Set<UUID> blockIds) {
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
    public Set<BlockmessChain<E, C, P>> getPriorityChains() {
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
    public void spawnPermanentChildren(UUID lftId, UUID rgtId)
            throws PrototypeHasNotBeenDefinedException {
        this.inner.spawnPermanentChildren(lftId, rgtId);
    }

    @Override
    public void submitContentDirectly(Collection<StructuredValue<E>> content) {
        inner.submitContentDirectly(content);
    }

    @Override
    public int countReferencedPermanent() {
        return 2 + inner.countReferencedPermanent();
    }

    @Override
    public void replaceChild(BlockmessChain<E,C,P> newChild) {
        inner = newChild;
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
        return parent.getTreeRoot();
    }

    @Override
    public void spawnChildren(UUID originator) throws PrototypeHasNotBeenDefinedException {
        inner.spawnChildren(originator);
    }

    @Override
    public Set<UUID> mergeChildren() throws LedgerTreeNodeDoesNotExistException {
        if (inner.isLeaf()) {
            skipThisNode();
            //inner.resetSamples();
            inner.lowerLeafDepth();
            inner.doubleChainThroughput();
            inner.submitContent(lft.getStoredContent());
            inner.submitContent(rgt.getStoredContent());
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
    public Set<UUID> getFinalizedIds() {
        return ((DebugBlockmessChain<E,C,P>)inner).getFinalizedIds();
    }

    @Override
    public Set<UUID> getNodesIds() {
        return ((DebugBlockmessChain<E,C,P>)inner).getNodesIds();
    }

    @Override
    public Set<UUID> getForkBlocks(int depth) {
        return ((DebugBlockmessChain<E,C,P>)inner).getForkBlocks(depth);
    }

    @Override
    public int getNumSamples() {
        return ((DebugBlockmessChain<E,C,P>)inner).getNumSamples();
    }

    @Override
    public int getNumUnderloaded() {
        return ((DebugBlockmessChain<E,C,P>)inner).getNumUnderloaded();
    }

    @Override
    public int getNumOverloaded() {
        return ((DebugBlockmessChain<E,C,P>)inner).getNumOverloaded();
    }

    @Override
    public int getFinalizedWeight() {
        return ((DebugBlockmessChain<E,C,P>)inner).getFinalizedWeight();
    }

    @Override
    public boolean isOverloaded() {
        return ((DebugBlockmessChain<E,C,P>)inner).isOverloaded();
    }

    @Override
    public int getMaxBlockSize() {
        return ((DebugBlockmessChain<E,C,P>)inner).getMaxBlockSize();
    }

    @Override
    public boolean hasTemporaryChains() {
        return ((DebugBlockmessChain<E,C,P>)inner).hasTemporaryChains();
    }

    @Override
    public int getNumChaining() {
        return 1 + ((DebugBlockmessChain<E,C,P>)inner).getNumChaining();
    }

    @Override
    public int getNumSpawnedChains() {
        return 2 + ((DebugBlockmessChain<E,C,P>)inner).getNumSpawnedChains();
    }

    @Override
    public List<DebugBlockmessChain<E, C, P>> getSpawnedChains() {
        List<DebugBlockmessChain<E,C,P>> spawnedChains = new ArrayList<>(List.of(lft, rgt));
        spawnedChains.addAll(((DebugBlockmessChain<E,C,P>)inner).getSpawnedChains());
        return spawnedChains;
    }

    @Override
    public int getNumFinalizedPending() {
        return ((DebugBlockmessChain<E,C,P>)inner).getNumFinalizedPending();
    }

    @Override
    public List<StructuredValue<E>> generateBlockContentList(Collection<UUID> states, int usedSpace)
            throws IOException {
        return inner.generateBlockContentList(states, usedSpace);
    }

    @Override
    public List<StructuredValue<E>> generateBoundBlockContentList(Collection<UUID> states, int usedSpace, int maxTxs)
            throws IOException {
        return inner.generateBoundBlockContentList(states, usedSpace, maxTxs);
    }

    @Override
    public void submitContent(Collection<StructuredValue<E>> content) {
        content.forEach(this::submitContent);
    }

    @Override
    public void submitContent(StructuredValue<E> content) {
        StructuredValueMask.MaskResult res = content.matchIds();
        content.advanceMask();
        switch (res) {
            case LEFT:
                lft.submitContent(content);
                break;
            case RIGHT:
                rgt.submitContent(content);
                break;
            case CENTER:
                inner.submitContent(content);
        }
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        lft.deleteContent(contentIds);
        rgt.deleteContent(contentIds);
        inner.deleteContent(contentIds);
    }

    @Override
    public Collection<StructuredValue<E>> getStoredContent() {
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
    public Pair<ComposableContentStorage<E>, ComposableContentStorage<E>> separateContent(
            StructuredValueMask mask, ContentStorage<StructuredValue<E>> innerLft,
            ContentStorage<StructuredValue<E>> innerRgt) {
        return inner.separateContent(mask, innerLft, innerRgt);
    }

    @Override
    public void aggregateContent(Collection<ComposableContentStorage<E>> composableBlockConstructors) {
        inner.aggregateContent(composableBlockConstructors);
    }
}
