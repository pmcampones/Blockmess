package valueDispatcher.requests;

import catecoin.txs.Transaction;
import ledger.ledgerManager.StructuredValue;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class DisseminateTransactionRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final StructuredValue<Transaction> tx;

    public DisseminateTransactionRequest(StructuredValue<Transaction> tx) {
        super(ID);
        this.tx = tx;
    }

    public StructuredValue<Transaction> getTransaction() {
        return tx;
    }

}