package catecoin.blockConstructors;

import catecoin.txs.SlimTransaction;
import ledger.ledgerManager.StructuredValue;

import java.util.Collection;

public class StructuredValuesTxLoader {

    private final ContentStorage<StructuredValue<SlimTransaction>> contentStorage;

    public StructuredValuesTxLoader(ContentStorage<StructuredValue<SlimTransaction>> contentStorage) {
        this.contentStorage = contentStorage;
    }

    public void loadTxs(Collection<StructuredValue<SlimTransaction>> txs) {
        contentStorage.submitContent(txs);
    }
}
