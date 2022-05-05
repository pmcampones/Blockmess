package valueDispatcher.requests;

import catecoin.blocks.ContentList;
import catecoin.txs.IndexableContent;
import ledger.blocks.LedgerBlock;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import sybilResistantElection.SybilElectionProof;
import utils.IDGenerator;

public class DisseminateSignedBlockRequest<B extends LedgerBlock<? extends ContentList<? extends IndexableContent>, ? extends SybilElectionProof>>
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
