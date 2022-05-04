package catecoin.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

public class DeliverFinalizedBlocksContentNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    public DeliverFinalizedBlocksContentNotification() {
        super(ID);
    }

}
