package broadcastProtocols.lazyPush.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;
import valueDispatcher.DispatcherWrapper;

public class LazyBroadcastRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final DispatcherWrapper val;

    public LazyBroadcastRequest(DispatcherWrapper val) {
        super(ID);
        this.val = val;
    }

    public DispatcherWrapper getVal() {
        return val;
    }
}
