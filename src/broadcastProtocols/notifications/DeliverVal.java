package broadcastProtocols.notifications;

import broadcastProtocols.BroadcastValue;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverVal extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final BroadcastValue val;

    public DeliverVal(BroadcastValue val) {
        super(ID);
        this.val = val;
    }

    public BroadcastValue getVal() {
        return val;
    }
}
