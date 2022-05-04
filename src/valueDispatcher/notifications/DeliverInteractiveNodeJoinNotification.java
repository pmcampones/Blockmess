package valueDispatcher.notifications;

import catecoin.nodeJoins.InteractiveNodeJoin;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverInteractiveNodeJoinNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    public DeliverInteractiveNodeJoinNotification() {
        super(ID);
    }

}
