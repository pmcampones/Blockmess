package valueDispatcher.notifications;

import catecoin.nodeJoins.InteractiveNodeJoin;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverInteractiveNodeJoinNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final InteractiveNodeJoin interactiveNodeJoin;

    public DeliverInteractiveNodeJoinNotification(InteractiveNodeJoin interactiveNodeJoin) {
        super(ID);
        this.interactiveNodeJoin = interactiveNodeJoin;
    }

    public InteractiveNodeJoin getNodeJoin() {
        return interactiveNodeJoin;
    }
}
