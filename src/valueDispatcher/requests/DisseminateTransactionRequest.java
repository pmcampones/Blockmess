package valueDispatcher.requests;

import catecoin.txs.SlimTransaction;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class DisseminateTransactionRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final SlimTransaction tx;

    public DisseminateTransactionRequest(SlimTransaction tx) {
        super(ID);
        this.tx = tx;
    }

    public SlimTransaction getTransaction() {
        return tx;
    }

}