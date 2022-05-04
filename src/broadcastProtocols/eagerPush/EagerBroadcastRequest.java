package broadcastProtocols.eagerPush;

import main.ProtoPojo;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class EagerBroadcastRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final ProtoPojo val;

    public EagerBroadcastRequest(ProtoPojo val) {
        super(ID);
        this.val = val;
    }

    public ProtoPojo getVal() {
        return val;
    }
}
