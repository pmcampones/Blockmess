package broadcastProtocols.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class PeerUnreachableNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    public PeerUnreachableNotification() {
        super(ID);
    }

}
