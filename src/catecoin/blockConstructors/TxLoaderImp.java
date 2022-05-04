package catecoin.blockConstructors;

import catecoin.txs.SerializableTransaction;
import catecoin.txs.SlimTransaction;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TxLoaderImp implements TxsLoader<SlimTransaction> {

    private final ContentStorage<SlimTransaction> contentStorage;

    public TxLoaderImp(ContentStorage<SlimTransaction> contentStorage) {
        this.contentStorage = contentStorage;
    }

    public static Collection<SlimTransaction> loadSlimTxsFromFile(String filePath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String txsContent = Files.readString(Path.of(filePath));
        SerializableTransaction[] stxs = new Gson().fromJson(txsContent, SerializableTransaction[].class);
        return convertToSlimTxs(stxs);
    }

    private static Collection<SlimTransaction> convertToSlimTxs(SerializableTransaction[] serializableTransactions)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        List<SlimTransaction> txs = new ArrayList<>(serializableTransactions.length);
        for (SerializableTransaction stx : serializableTransactions)
            txs.add(stx.toRegularTx());
        return txs;
    }

    @Override
    public void loadTxs(Collection<SlimTransaction> txs) {
        contentStorage.submitContent(txs);
    }
}
