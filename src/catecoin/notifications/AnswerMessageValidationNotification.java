package catecoin.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

import java.util.UUID;

public class AnswerMessageValidationNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final UUID blockingMessageID;

    public AnswerMessageValidationNotification(UUID blockingMessageID) {
        super(ID);
        this.blockingMessageID = blockingMessageID;
    }

    public UUID getBlockingMessageID() {
        return blockingMessageID;
    }

}
