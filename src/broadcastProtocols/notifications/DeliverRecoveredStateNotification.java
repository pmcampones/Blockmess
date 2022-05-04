package broadcastProtocols.notifications;

import broadcastProtocols.messages.BatcheableMessage;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

import java.util.Set;

public class DeliverRecoveredStateNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final Set<BatcheableMessage> recoveredMessages;

    public DeliverRecoveredStateNotification(Set<BatcheableMessage> recoveredMessages) {
        super(ID);
        this.recoveredMessages = recoveredMessages;
    }

    public Set<BatcheableMessage> getRecoveredMessages() {
        return recoveredMessages;
    }
}
