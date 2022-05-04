package catecoin.txs;

import ledger.ledgerManager.StructuredValue;
import main.CryptographicUtils;

public class StructuredValueSlimTransactionWrapper {

    public static StructuredValue<SlimTransaction> wrapTx(SlimTransaction tx) {
        byte[] id1 = CryptographicUtils.hashInput(tx.getOrigin().getEncoded());
        byte[] id2 = CryptographicUtils.hashInput(tx.getDestination().getEncoded());
        return new StructuredValue<>(id1, id2, tx);
    }

}
