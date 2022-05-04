package ledger;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantElection.SybilElectionProof;

import java.util.Set;
import java.util.UUID;

public interface Ledger<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>,? extends SybilElectionProof>> {

    /**
     * Retrieves the references to the previous blocks in the ledger.
     */
    Set<UUID> getBlockR();

    /**
     * Submits a block to the Ledger instance
     * @param block A signed block ready to be accepted by the application.
     */
    void submitBlock(B block);

    /**
     * Adds a subscriber to changes of state in the Ledger according with the Observer design pattern.
     * @param observer The object implementing LedgerObserver that will be notified upon state modifications.
     */
    void attachObserver(LedgerObserver<B> observer);

    /**
     * Retrieves the identifiers of blocks in a Ledger at a given distance (in weight) from a parameter block.
     * @param block The block from which the other blocks will begin to be searched.
     * @param distance The distance to the original node.
     * @return A set of blocks identifiers at a given distance from the block received as parameter.
     * @throws IllegalArgumentException Thrown if the distance is negative or the block does not exist.
     */
    Set<UUID> getFollowing(UUID block, int distance) throws IllegalArgumentException;

    int getWeight(UUID block) throws IllegalArgumentException;

    boolean isInLongestChain(UUID nodeId);

    void close();

}
