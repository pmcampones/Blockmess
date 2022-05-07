package valueDispatcher.requests;

import ledger.blocks.BlockmessBlock;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class DisseminateSignedBlockRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final BlockmessBlock block;

    public DisseminateSignedBlockRequest(BlockmessBlock block) {
        super(ID);
        this.block = block;
    }

    public BlockmessBlock getBlock() {
        return block;
    }
}
