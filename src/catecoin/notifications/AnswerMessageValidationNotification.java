package catecoin.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

import java.util.UUID;

public class AnswerMessageValidationNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final boolean valid;

    private final UUID blockingMessageID;

    public AnswerMessageValidationNotification(boolean valid, UUID blockingMessageID) {
        super(ID);
        this.valid = valid;
        this.blockingMessageID = blockingMessageID;
    }

    public boolean isValid() {
        return valid;
    }

    public UUID getBlockingMessageID() {
        return blockingMessageID;
    }

}
