package valueDispatcher.requests;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import sybilResistantCommitteeElection.SybilElectionProof;
import utils.IDGenerator;

public class DisseminateSignedBlockRequest<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>, ? extends SybilElectionProof>>
        extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final B block;

    public DisseminateSignedBlockRequest(B block) {
        super(ID);
        this.block = block;
    }

    public B getBlock() {
        return block;
    }
}
