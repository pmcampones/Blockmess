package ledger.ledgerManager.nodes;

import cmux.AppOperation;
import cmux.CMuxMask;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import lombok.experimental.Delegate;

import java.util.Collection;
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
    @Delegate(excludes = ExcludeParent.class)
    private ParentTreeNode parent;
    @Delegate(excludes = ExcludeInnerBlockmessChain.class)
    private BlockmessChain inner;

    public PermanentChainNode(ParentTreeNode parent, BlockmessChain inner,
                               ReferenceNode lft, ReferenceNode rgt) {
        this.parent = parent;
        this.inner = inner;
        this.lft = lft;
        this.rgt = rgt;
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
    public boolean shouldMerge() {
        if (inner.isLeaf())
            return inner.isUnderloaded()
                    && lft.isLeaf() && lft.isUnderloaded()
                    && rgt.isLeaf() && rgt.isUnderloaded();
        return inner.shouldMerge();
    }

    @Override
    public Set<BlockmessChain> getPriorityChains() {
        return union(inner.getPriorityChains(), union(lft.getPriorityChains(), rgt.getPriorityChains()));
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

    private interface ExcludeParent {
        void replaceChild(BlockmessChain newChild);
    }

    private interface ExcludeInnerBlockmessChain {
        void replaceParent(ParentTreeNode parent);
        boolean isLeaf();
        boolean shouldMerge();
        Set<BlockmessChain> getPriorityChains();
        int countReferencedPermanent();
        Set<UUID> mergeChildren();
        void submitOperations(Collection<AppOperation> operations);
        void submitOperation(AppOperation operation);
        void deleteOperations(Set<UUID> operatationIds);
    }

}
