package valueDispatcher.requests;

import ledger.ledgerManager.StructuredValue;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class DisseminateTransactionRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final StructuredValue tx;

    public DisseminateTransactionRequest(StructuredValue tx) {
        super(ID);
        this.tx = tx;
    }

    public StructuredValue getTransaction() {
        return tx;
    }

}