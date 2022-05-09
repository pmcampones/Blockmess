package broadcastProtocols.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;
import valueDispatcher.DispatcherWrapper;

public class DeliverVal extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final DispatcherWrapper val;

    public DeliverVal(DispatcherWrapper val) {
        super(ID);
        this.val = val;
    }

    public DispatcherWrapper getVal() {
        return val;
    }
}
