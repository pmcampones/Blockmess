package catecoin.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class BalanceRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    public BalanceRequest() {
        super(ID);
    }
}
