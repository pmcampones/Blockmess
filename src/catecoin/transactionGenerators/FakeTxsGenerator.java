package catecoin.transactionGenerators;

import catecoin.txs.Transaction;
import org.jetbrains.annotations.NotNull;
import utils.CryptographicUtils;

import java.io.IOException;
import java.security.*;
import java.util.*;

public class FakeTxsGenerator {

    public Collection<Transaction> generateFakeTxs(int numTxs) {
        try {
            return tryToGenerateFakeTxs(numTxs);
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new Error();
        }
    }

    @NotNull
    private List<Transaction> tryToGenerateFakeTxs(int numTxs) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        List<Transaction> txs = new ArrayList<>(numTxs);
        for (int i = 0; i < numTxs; i++) {
            KeyPair origin = CryptographicUtils.generateECDSAKeyPair();
            KeyPair destination = CryptographicUtils.generateECDSAKeyPair();
            txs.add(generateTransaction(destination.getPublic(), origin));
        }
        return txs;
    }

    private Transaction generateTransaction(PublicKey destination, KeyPair origin) {
        try {
            return tryToGenerateTransaction(destination, origin);
        } catch (IOException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    @NotNull
    private Transaction tryToGenerateTransaction(PublicKey destination, KeyPair origin)
            throws IOException, SignatureException, InvalidKeyException {
        List<UUID> input = List.of(
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        List<Integer> destinationAmount = List.of(
                1 + new Random().nextInt(10000)
        );
        List<Integer> originAmount = List.of(
                1 + new Random().nextInt(10000)
        );
        return new Transaction(origin.getPublic(), destination, input,
                destinationAmount, originAmount, origin.getPrivate());
    }

}
