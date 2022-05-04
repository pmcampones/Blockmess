package catecoin.transactionGenerators;

import catecoin.txs.SlimTransaction;
import utils.CryptographicUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.*;
import java.util.*;

public class FakeTxsGenerator {

    public Collection<SlimTransaction> generateFakeTxs(int numTxs) {
        try {
            return tryToGenerateFakeTxs(numTxs);
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new Error();
        }
    }

    @NotNull
    private List<SlimTransaction> tryToGenerateFakeTxs(int numTxs) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        List<SlimTransaction> txs = new ArrayList<>(numTxs);
        for (int i = 0; i < numTxs; i++) {
            KeyPair origin = CryptographicUtils.generateECDSAKeyPair();
            KeyPair destination = CryptographicUtils.generateECDSAKeyPair();
            txs.add(generateTransaction(destination.getPublic(), origin));
        }
        return txs;
    }

    private SlimTransaction generateTransaction(PublicKey destination, KeyPair origin) {
        try {
            return tryToGenerateTransaction(destination, origin);
        } catch (IOException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    @NotNull
    private SlimTransaction tryToGenerateTransaction(PublicKey destination, KeyPair origin)
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
        return new SlimTransaction(origin.getPublic(), destination, input,
                destinationAmount, originAmount, origin.getPrivate());
    }

}
