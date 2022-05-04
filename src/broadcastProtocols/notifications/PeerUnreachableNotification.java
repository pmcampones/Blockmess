package broadcastProtocols.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.IDGenerator;

public class PeerUnreachableNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final Host peer;

    public PeerUnreachableNotification(Host peer) {
        super(ID);
        this.peer = peer;
    }

    public Host getPeer() {
        return peer;
    }

}
