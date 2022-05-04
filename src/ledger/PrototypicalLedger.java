package ledger;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.UUID;

public interface PrototypicalLedger<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>, ? extends SybilElectionProof>>
        extends Ledger<B> {

    PrototypicalLedger<B> clonePrototype(UUID genesisId);

}
