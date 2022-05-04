package valueDispatcher.notifications;

import catecoin.nodeJoins.AutomatedNodeJoin;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverAutomatedNodeJoinNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final AutomatedNodeJoin automatedNodeJoin;

    public DeliverAutomatedNodeJoinNotification(AutomatedNodeJoin automatedNodeJoin) {
        super(ID);
        this.automatedNodeJoin = automatedNodeJoin;
    }

    public AutomatedNodeJoin getAutomatedNodeJoin() {
        return automatedNodeJoin;
    }
}
