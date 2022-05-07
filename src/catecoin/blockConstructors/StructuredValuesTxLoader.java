package catecoin.blockConstructors;

import catecoin.txs.Transaction;
import ledger.ledgerManager.StructuredValue;

import java.util.Collection;

public class StructuredValuesTxLoader {

    private final ContentStorage contentStorage;

    public StructuredValuesTxLoader(ContentStorage contentStorage) {
        this.contentStorage = contentStorage;
    }

    public void loadTxs(Collection<StructuredValue<Transaction>> txs) {
        contentStorage.submitContent(txs);
    }
}
