package valueDispatcher.requests;

import catecoin.txs.Transaction;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class DisseminateTransactionRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final Transaction tx;

    public DisseminateTransactionRequest(Transaction tx) {
        super(ID);
        this.tx = tx;
    }

    public Transaction getTransaction() {
        return tx;
    }

}