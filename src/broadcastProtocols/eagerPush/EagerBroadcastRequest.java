package broadcastProtocols.eagerPush;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;
import valueDispatcher.DispatcherWrapper;

public class EagerBroadcastRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final DispatcherWrapper val;

    public EagerBroadcastRequest(DispatcherWrapper val) {
        super(ID);
        this.val = val;
    }

    public DispatcherWrapper getVal() {
        return val;
    }
}
