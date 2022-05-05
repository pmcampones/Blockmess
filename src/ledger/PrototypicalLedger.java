package ledger;

import catecoin.blocks.ContentList;
import catecoin.txs.IndexableContent;
import ledger.blocks.LedgerBlock;
import sybilResistantElection.SybilElectionProof;

import java.util.UUID;

public interface PrototypicalLedger<B extends LedgerBlock<? extends ContentList<? extends IndexableContent>, ? extends SybilElectionProof>>
        extends Ledger<B> {

    PrototypicalLedger<B> clonePrototype(UUID genesisId);

}
