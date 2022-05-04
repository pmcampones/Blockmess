package catecoin.blockConstructors;

import catecoin.txs.SlimTransaction;
import ledger.ledgerManager.StructuredValue;

import java.util.Collection;

public class StructuredValuesTxLoader implements TxsLoader<StructuredValue<SlimTransaction>> {

    private final ContentStorage<StructuredValue<SlimTransaction>> contentStorage;

    public StructuredValuesTxLoader(ContentStorage<StructuredValue<SlimTransaction>> contentStorage) {
        this.contentStorage = contentStorage;
    }

    @Override
    public void loadTxs(Collection<StructuredValue<SlimTransaction>> txs) {
        contentStorage.submitContent(txs);
    }
}
