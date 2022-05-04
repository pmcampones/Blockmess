package broadcastProtocols.lazyPush.requests;

import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class LazyBroadcastRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final ProtoPojo val;

    public LazyBroadcastRequest(ProtoPojo val) {
        super(ID);
        this.val = val;
    }

    public ProtoPojo getVal() {
        return val;
    }
}
