package catecoin.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

import java.security.PublicKey;

public class SendTransactionRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final PublicKey destination;

    private final int amount;

    public SendTransactionRequest(PublicKey destination, int amount) {
        super(ID);
        this.destination = destination;
        this.amount = amount;
    }

    public PublicKey getTransactionDestination() {
        return destination;
    }

    public int getTransactionAmount() {
        return amount;
    }
}
