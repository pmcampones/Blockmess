package ledger.ledgerManager.nodes;

import cmux.AppOperation;
import cmux.CMuxMask;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import lombok.experimental.Delegate;
import operationMapper.ComposableOperationMapper;
import org.apache.commons.lang3.tuple.Pair;
import utils.CryptographicUtils;

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
public class TempChainNode implements InnerNode, LedgerObserver, BlockmessChain {

    private final Properties props;

    /**
     * Maps the identifier of the Chain root block to the Chains that are originated.
     * <p>Eventually, one and only one of the root blocks in this DS will originate a valid Chain.</p>
     */
    private final Map<UUID, Pair<ReferenceNode, ReferenceNode>> tentativeChains = new HashMap<>();
    private final Pair<ComposableOperationMapper, ComposableOperationMapper> contentStoragePair;

    @Delegate(excludes = ExcludeParent.class)
    private ParentTreeNode parent;

    private final int finalizedWeight;

    private final int rootWeight;

    /**
     * How deep is this Chain in the Blockmess Tree
     */
    private final int chainDepth;

    @Delegate(excludes = ExcludeInnerBlockmessChain.class)
    private BlockmessChain inner;

    public TempChainNode(
            Properties props, BlockmessChain inner, ParentTreeNode parent,
            UUID ChainOriginatorBlockId, int chainDepth,
            Pair<ComposableOperationMapper, ComposableOperationMapper> contentStoragePair) {
        this.props = props;
        this.inner = inner;
        inner.attachObserver(this);
        this.parent = parent;
        this.finalizedWeight = parseInt(props.getProperty("finalizedWeight", "6"));
        this.rootWeight = inner.getWeight(ChainOriginatorBlockId);
        this.chainDepth = chainDepth;
        this.contentStoragePair = contentStoragePair;
        fillChainMap(ChainOriginatorBlockId);
    }

    private void fillChainMap(UUID chainOriginatorBlockId) {
        Set<UUID> rootIds = inner.getFollowing(chainOriginatorBlockId, finalizedWeight + 3);
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

    private Pair<ReferenceNode, ReferenceNode> computeChains(BlockmessBlock root) {
        ParentTreeNode treeRoot = parent.getTreeRoot();
        UUID lftId = computeChainId(root.getBlockId(), "lft".getBytes());
        ReferenceNode lft = new ReferenceNode(props, lftId, treeRoot,
                root.getNextRank(), root.getNextRank(), chainDepth, contentStoragePair.getLeft(), root.getBlockId());
        UUID rgtId = computeChainId(root.getBlockId(), "rgt".getBytes());
        ReferenceNode rgt = new ReferenceNode(props, rgtId, treeRoot,
                root.getNextRank(), root.getNextRank(), chainDepth, contentStoragePair.getRight(), root.getBlockId());
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
    public void replaceParent(ParentTreeNode parent) {
        this.parent = parent;
    }

    @Override
    public Set<UUID> mergeChildren() throws LedgerTreeNodeDoesNotExistException {
        if (!inner.isLeaf())
            return inner.mergeChildren();
        skipThisNode();
        inner.resetSamples();
        inner.lowerLeafDepth();
        List<BlockmessChain> toMerge =  tentativeChains.values().stream()
                .map(p -> List.of(p.getLeft(), p.getRight()))
                .flatMap(Collection::stream)
                .collect(toList());
        toMerge.forEach(b -> inner.submitOperations(b.getStoredOperations()));
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
    public void replaceChild(BlockmessChain newChild) {
        this.inner = newChild;
    }

    @Override
    public void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
        if (weight == rootWeight + finalizedWeight + 3) {
            Pair<ReferenceNode, ReferenceNode> createdChains = computeChains(block);
            tentativeChains.put(block.getBlockId(), createdChains);
            parent.createChains(List.of(createdChains.getLeft(), createdChains.getRight()));
        }
    }

    @Override
    public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
        //Does nothing because any action taken by this class can only be done after the
        // ledger manager has linearized the blocks.
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
                contentStoragePair.getLeft().submitOperation(operation);
                break;
            case RIGHT:
                contentStoragePair.getRight().submitOperation(operation);
                break;
            case CENTER:
                inner.submitOperation(operation);
        }
    }

    @Override
    public void deleteOperations(Set<UUID> operatationIds) {
        for (var pair : tentativeChains.values()) {
            pair.getLeft().deleteOperations(operatationIds);
            pair.getRight().deleteOperations(operatationIds);
        }
        inner.deleteOperations(operatationIds);
    }

    private interface ExcludeInnerBlockmessChain {
        void replaceParent(ParentTreeNode parent);
        Set<UUID> mergeChildren();
        boolean isLeaf();
        BlockmessBlock deliverChainBlock();
        boolean shouldMerge();
        Set<BlockmessChain> getPriorityChains();
        int countReferencedPermanent();
        void submitOperations(Collection<AppOperation> operations);
        void submitOperation(AppOperation operation);
        void deleteOperations(Set<UUID> operatationIds);
    }

    private interface ExcludeParent {
        void replaceChild(BlockmessChain newChild);
    }

}
