package blockConstructors;

import ledger.AppContent;

import java.util.Collection;

public class StructuredValuesTxLoader {

    private final ContentStorage contentStorage;

    public StructuredValuesTxLoader(ContentStorage contentStorage) {
        this.contentStorage = contentStorage;
    }

    public void loadTxs(Collection<AppContent> txs) {
        contentStorage.submitContent(txs);
    }
}
