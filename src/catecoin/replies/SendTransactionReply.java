package catecoin.replies;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;
import utils.IDGenerator;

import java.security.PublicKey;
import java.util.Set;
import java.util.UUID;

public class SendTransactionReply extends ProtoReply {

    public static final short ID = IDGenerator.genId();

    private final boolean successful;

    private final PublicKey destination;

    private final int amount;

    public SendTransactionReply(boolean successful, PublicKey destination, int amount) {
        super(ID);
        this.successful = successful;
        this.destination = destination;
        this.amount = amount;
    }

    public boolean wasTheTransactionSuccessfullySent() {
        return successful;
    }

    public PublicKey getTransactionDestination() {
        return destination;
    }

    public int getTransactionAmount() {
        return amount;
    }

}
