package main;

import catecoin.transactionGenerators.FakeTxsGenerator;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.exceptions.InvalidParameterException;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Properties;

import static java.lang.Integer.parseInt;

public class StoreFakeTxsMain {

    public static void main(String[] args) throws InvalidParameterException,
            IOException, NoSuchAlgorithmException, InvalidKeySpecException,
            InvalidAlgorithmParameterException {
        Properties props = Babel.loadConfig(new String[0], Main.DEFAULT_CONF);
        List<PublicKey> destinations = Main.loadKeys(props);
        for (String filepath : args)
            storeTxs(props, destinations, filepath);
    }

    private static void storeTxs(Properties props, List<PublicKey> destinations, String filepath)
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, IOException {
        KeyPair keys = CryptographicUtils.generateECDSAKeyPair();
        int numTxs = parseInt(props.getProperty("numBootstrapTxs", "10000"));
        new FakeTxsGenerator(keys)
                .storeTxs(destinations, numTxs, filepath);
    }

}
