package test.java;

import catecoin.blockConstructors.ContentStorage;
import catecoin.blockConstructors.ContextObliviousContentStorage;
import catecoin.blockConstructors.TxLoaderImp;
import catecoin.blockConstructors.TxsLoader;
import catecoin.transactionGenerators.FakeTxsGenerator;
import catecoin.txs.SlimTransaction;
import main.CryptographicUtils;
import main.Main;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.exceptions.InvalidParameterException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BootstrapTxsTests {

    public static final String TEST_FILEPATH = "./bootstrapContent/test/txs/test.txt";

    private final Properties props = Babel.loadConfig(new String[0], Main.DEFAULT_TEST_CONF);

    public BootstrapTxsTests() throws InvalidParameterException, IOException {
        if(Files.notExists(Path.of(TEST_FILEPATH)))
            Files.createFile(Path.of(TEST_FILEPATH));
    }

    @Test
    void shouldLoadValidTxs() throws Exception {
        KeyPair keys = CryptographicUtils.generateECDSAKeyPair();
        List<PublicKey> nodes = genNodes();
        new FakeTxsGenerator(keys)
                .storeTxs(nodes, 100000, TEST_FILEPATH);
        ContentStorage<SlimTransaction> contentStorage = new ContextObliviousContentStorage<>(props, null);
        TxsLoader loader = new TxLoaderImp(contentStorage);
        Collection<SlimTransaction> txs = loader.loadFromFile(TEST_FILEPATH);
        assertTrue(txs.stream().allMatch(SlimTransaction::hasValidSemantics));
    }

    private static List<PublicKey> genNodes() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        int numNodes = 10;
        List<PublicKey> nodes = new ArrayList<>(numNodes);
        for (int i = 0; i < numNodes; i++)
            nodes.add(CryptographicUtils.generateECDSAKeyPair().getPublic());
        return nodes;
    }

}
