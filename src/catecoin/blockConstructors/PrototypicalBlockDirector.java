package catecoin.blockConstructors;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantCommitteeElection.SybilElectionProof;

public interface PrototypicalBlockDirector<E extends IndexableContent, C extends BlockContent<E>, B extends LedgerBlock<C,P>, P extends SybilElectionProof>
        extends BlockDirector<E,C,B,P> {

    PrototypicalBlockDirector<E,C,B,P> clonePrototype();
}
