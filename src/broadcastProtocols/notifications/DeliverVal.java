package broadcastProtocols.notifications;

import main.ProtoPojo;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverVal extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final ProtoPojo val;

    public DeliverVal(ProtoPojo val) {
        super(ID);
        this.val = val;
    }

    public ProtoPojo getVal() {
        return val;
    }
}
