package ledger.ledgerManager.nodes;

import catecoin.blockConstructors.ComposableContentStorage;
import catecoin.blocks.ContentList;
import catecoin.txs.IndexableContent;
import ledger.Ledger;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import sybilResistantElection.SybilElectionProof;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface BlockmessChain<E extends IndexableContent, C extends ContentList<StructuredValue<E>>, P extends SybilElectionProof>
        extends Ledger<BlockmessBlock<C,P>>, ComposableContentStorage<E> {

    UUID getChainId();

    void replaceParent(ParentTreeNode<E,C,P> parent);

    void spawnChildren(UUID originator) throws PrototypeHasNotBeenDefinedException;

    /**
     * Merges children in this Chain.
     * @return The identifiers of the merged children
     * @throws LedgerTreeNodeDoesNotExistException This Chain has no children to merge with.
     */
    Set<UUID> mergeChildren() throws LedgerTreeNodeDoesNotExistException;

    boolean isLeaf();

    /**
     * Inquires whether this Chain has any finalized blocks
     */
    boolean hasFinalized();

    /**
     * Gets the first finalized block in the Chain that is yet to be delivered to the application.
     * @return The first block or null if no block was finalized.
     */
    BlockmessBlock<C,P> peekFinalized();

    /**
     * Removes the first finalized block in the Chain that is yet to be delivered to the application.
     * <p>Processes the block delivery.</p>
     * @return The block removed or null if no block was finalized.
     */
    BlockmessBlock<C,P> deliverChainBlock();

    /**
     * Queries whether this Chain should spawn two sub-Chains to alleviate its load.
     */
    boolean shouldSpawn();

    /**
     * Queries whether this Chain should merge with its child Chains.
     */
    boolean shouldMerge();

    /**
     * Queries whether this Chain's load is too low to warrant it's existence.
     */
    boolean isUnderloaded();

    /**
     * Retrieves the lowest possible rank a valid block in this Chain can have.
     * <p>This can be used to not requiring newly created Chains finalizing blocks
     * in order to establish concrete Chains.</p>
     * <p>Without this, the Adversary could compromise the liveness of the system by
     * forking many many temporary Chains and thus stopping the system from linearizing blocks
     * to be delivered to the application.</p>
     * <p>This value will always be greater than the rank of the root node of the temporary Chain,
     * as such, it can be finalized and delivered before these blocks.</p>
     */
    long getMinimumRank();

    Set<BlockmessBlock<C,P>> getBlocks(Set<UUID> blockIds);

    void resetSamples();

    long getRankFromRefs(Set<UUID> refs);

    Set<BlockmessChain<E,C,P>> getPriorityChains();

    void lowerLeafDepth();

    long getNextRank();

    void spawnPermanentChildren(UUID lftId, UUID rgtId)
            throws PrototypeHasNotBeenDefinedException;

    /**
     * Submits content to the Chain without multiplexing it to other Chains.
     * <p>This is used to test the wasted performance of other parallel chain solutions that
     * allow repeated transactions in several chains.</p>
     */
    void submitContentDirectly(Collection<StructuredValue<E>> content);

    int countReferencedPermanent();
}
