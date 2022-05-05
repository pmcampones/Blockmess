package ledger.blocks;

import catecoin.blocks.ContentList;
import sybilResistantElection.SybilElectionProof;

import java.util.UUID;

public interface BlockmessBlock<C extends ContentList<?>, P extends SybilElectionProof>
        extends LedgerBlock<C, P> {

    /**
     * Retreives the Chain in the {@link ledger.ledgerManager.LedgerManager}
     * Chain where this block is to be placed.
     */
    UUID getDestinationChain();

    long getBlockRank();

    long getNextRank();
}
