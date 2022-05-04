package ledger;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.UUID;

public interface PrototypicalLedger<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>, ? extends SybilElectionProof>>
        extends Ledger<B> {

    /**
     * Clones the parameterizable aspects of the Ledger, not its content.
     */
    PrototypicalLedger<B> clonePrototype();

    PrototypicalLedger<B> clonePrototype(UUID genesisId);

}
