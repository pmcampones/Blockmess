package catecoin.blockConstructors;

import catecoin.txs.Transaction;
import ledger.ledgerManager.StructuredValue;

import java.util.Collection;

public class StructuredValuesTxLoader {

    private final ContentStorage<StructuredValue<Transaction>> contentStorage;

    public StructuredValuesTxLoader(ContentStorage<StructuredValue<Transaction>> contentStorage) {
        this.contentStorage = contentStorage;
    }

    public void loadTxs(Collection<StructuredValue<Transaction>> txs) {
        contentStorage.submitContent(txs);
    }
}
