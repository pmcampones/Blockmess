package valueDispatcher.requests;

import ledger.ledgerManager.AppContent;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class DisseminateTransactionRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final AppContent tx;

    public DisseminateTransactionRequest(AppContent tx) {
        super(ID);
        this.tx = tx;
    }

    public AppContent getTransaction() {
        return tx;
    }

}