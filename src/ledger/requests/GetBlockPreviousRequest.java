package ledger.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class GetBlockPreviousRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    public GetBlockPreviousRequest() {
        super(ID);
    }
}
