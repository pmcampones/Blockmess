package valueDispatcher.notifications;

import catecoin.nodeJoins.AutomatedNodeJoin;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverAutomatedNodeJoinNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    public DeliverAutomatedNodeJoinNotification() {
        super(ID);
    }

}
