package catecoin.blockConstructors;

import catecoin.txs.SlimTransaction;
import ledger.ledgerManager.StructuredValue;
import main.CryptographicUtils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collection;
import java.util.stream.Collectors;

public class StructuredValuesTxLoader implements TxsLoader<StructuredValue<SlimTransaction>> {

    private final ContentStorage<StructuredValue<SlimTransaction>> contentStorage;

    public StructuredValuesTxLoader(ContentStorage<StructuredValue<SlimTransaction>> contentStorage) {
        this.contentStorage = contentStorage;
    }

    @Override
    public Collection<StructuredValue<SlimTransaction>> loadFromFile(String filePath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        Collection<SlimTransaction> txs = TxLoaderImp.loadSlimTxsFromFile(filePath);
        return txs.stream().map(tx -> {
            byte[] originBytes = CryptographicUtils.hashInput(tx.getOrigin().getEncoded());
            byte[] destinationBytes = CryptographicUtils.hashInput(tx.getDestination().getEncoded());
            return new StructuredValue<>(originBytes, destinationBytes, tx);
        }).collect(Collectors.toList());
    }

    @Override
    public void loadTxs(Collection<StructuredValue<SlimTransaction>> txs) {
        contentStorage.submitContent(txs);
    }
}
