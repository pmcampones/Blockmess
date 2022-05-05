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
import sybilResistantElection.SybilResistantElectionProof;

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
public class PermanentChainNode<E extends IndexableContent>
        implements InnerNode<E,ContentList<StructuredValue<E>>,SybilResistantElectionProof>, BlockmessChain<E>{

    private final ReferenceNode<E> lft, rgt;
    private ParentTreeNode<E,ContentList<StructuredValue<E>>,SybilResistantElectionProof> parent;
    private BlockmessChain<E> inner;

    public PermanentChainNode(ParentTreeNode<E,ContentList<StructuredValue<E>>,SybilResistantElectionProof> parent, BlockmessChain<E> inner,
                               ReferenceNode<E> lft, ReferenceNode<E> rgt) {
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
    public void submitBlock(BlockmessBlock<ContentList<StructuredValue<E>>,SybilResistantElectionProof> block) {
        inner.submitBlock(block);
    }

    @Override
    public void attachObserver(LedgerObserver<BlockmessBlock<ContentList<StructuredValue<E>>, SybilResistantElectionProof>> observer) {
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
    public void replaceParent(ParentTreeNode<E,ContentList<StructuredValue<E>>,SybilResistantElectionProof> parent) {
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
    public BlockmessBlock<ContentList<StructuredValue<E>>, SybilResistantElectionProof> peekFinalized() {
        return inner.peekFinalized();
    }

    @Override
    public BlockmessBlock<ContentList<StructuredValue<E>>, SybilResistantElectionProof> deliverChainBlock() {
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
    public Set<BlockmessBlock<ContentList<StructuredValue<E>>, SybilResistantElectionProof>> getBlocks(Set<UUID> blockIds) {
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
    public Set<BlockmessChain<E>> getPriorityChains() {
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
    public void replaceChild(BlockmessChain<E> newChild) {
        inner = newChild;
    }

    @Override
    public void forgetUnconfirmedChains(Set<UUID> discartedChainsIds) {
        parent.forgetUnconfirmedChains(discartedChainsIds);
    }

    @Override
    public void createChains(List<BlockmessChain<E>> createdChains) {
        parent.createChains(createdChains);
    }

    @Override
    public ParentTreeNode<E,ContentList<StructuredValue<E>>,SybilResistantElectionProof> getTreeRoot() {
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
    public List<StructuredValue<E>> generateContentListList(Collection<UUID> states, int usedSpace)
            throws IOException {
        return inner.generateContentListList(states, usedSpace);
    }

    @Override
    public List<StructuredValue<E>> generateBoundContentListList(Collection<UUID> states, int usedSpace, int maxTxs)
            throws IOException {
        return inner.generateBoundContentListList(states, usedSpace, maxTxs);
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
