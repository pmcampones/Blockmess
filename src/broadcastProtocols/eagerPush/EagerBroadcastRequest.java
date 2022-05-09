package broadcastProtocols.eagerPush;

import broadcastProtocols.BroadcastValue;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class EagerBroadcastRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final BroadcastValue val;

    public EagerBroadcastRequest(BroadcastValue val) {
        super(ID);
        this.val = val;
    }

    public BroadcastValue getVal() {
        return val;
    }
}
