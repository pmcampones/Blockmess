package catecoin.transactionGenerators;

import catecoin.txs.SerializableTransaction;
import catecoin.txs.SlimTransaction;
import com.google.gson.Gson;
import main.CryptographicUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FakeTxsGenerator {

    private final KeyPair self;

    public FakeTxsGenerator(KeyPair self) {
        this.self = self;
    }

    public void storeTxs(List<PublicKey> destinations, int numTxs, String filepath) throws IOException {
        Collection<SlimTransaction> txs = generateFakeTxs(destinations, numTxs);
        Collection<SerializableTransaction> serializableTxs = getSerializableTransactions(txs);
        String txsJson = new Gson().toJson(serializableTxs);
        Path path = Path.of(filepath);
        if (Files.notExists(path))
            Files.createFile(path);
        Files.writeString(path, txsJson);
    }

    public Collection<SlimTransaction> generateFakeTxs(List<PublicKey> destinations, int numTxs) {
        List<PublicKey> randomAccessDestinations = destinations instanceof RandomAccess ?
                destinations : new ArrayList<>(destinations);
        return IntStream.range(0, numTxs)
                .map(i -> i % randomAccessDestinations.size())
                .parallel()
                .mapToObj(randomAccessDestinations::get)
                .map(this::generateTransaction)
                .collect(Collectors.toSet());
    }

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

    private SlimTransaction generateTransaction(PublicKey destination) {
        try {
            return tryToGenerateTransaction(destination);
        } catch (IOException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
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

    private SlimTransaction tryToGenerateTransaction(PublicKey destination)
            throws IOException, SignatureException, InvalidKeyException {
        return tryToGenerateTransaction(destination, self);
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

    private Collection<SerializableTransaction> getSerializableTransactions(Collection<SlimTransaction> txs) {
        return txs.stream()
                .map(SerializableTransaction::new)
                .collect(Collectors.toSet());
    }

}
