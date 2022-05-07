package valueDispatcher.requests;

import catecoin.blocks.ContentList;
import ledger.blocks.LedgerBlock;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import sybilResistantElection.SybilResistantElectionProof;
import utils.IDGenerator;

public class DisseminateSignedBlockRequest<B extends LedgerBlock<? extends ContentList, ? extends SybilResistantElectionProof>>
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
