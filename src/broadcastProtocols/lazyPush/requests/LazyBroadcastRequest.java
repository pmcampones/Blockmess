package broadcastProtocols.lazyPush.requests;

import broadcastProtocols.BroadcastValue;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class LazyBroadcastRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final BroadcastValue val;

    public LazyBroadcastRequest(BroadcastValue val) {
        super(ID);
        this.val = val;
    }

    public BroadcastValue getVal() {
        return val;
    }
}
