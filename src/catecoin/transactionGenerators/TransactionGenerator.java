package catecoin.transactionGenerators;

import catecoin.exceptions.NotEnoughCoinsException;
import catecoin.mempoolManager.MempoolManager;
import catecoin.notifications.DeliverFinalizedBlocksContentNotification;
import catecoin.txs.SlimTransaction;
import catecoin.utxos.StorageUTXO;
import catecoin.replies.BalanceReply;
import catecoin.replies.SendTransactionReply;
import catecoin.requests.BalanceRequest;
import catecoin.requests.SendTransactionRequest;
import main.CryptographicUtils;
import main.ProtoPojo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import utils.IDGenerator;
import valueDispatcher.ValueDispatcher;
import valueDispatcher.requests.DisseminateTransactionRequest;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;

public class TransactionGenerator extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(TransactionGenerator.class);

    public static final short ID = IDGenerator.genId();

    private final PublicKey self;

    private final PrivateKey signer;

    public final ConcurrentMap<UUID, Integer> myUTXOs = new ConcurrentHashMap<>();

    public TransactionGenerator(Properties props, KeyPair myKeys) throws Exception {
        super(TransactionGenerator.class.getSimpleName(), ID);
        this.self = myKeys.getPublic();
        this.signer = myKeys.getPrivate();
        loadInitialUTXOs(props);
        ProtoPojo.pojoSerializers.put(SlimTransaction.ID, SlimTransaction.serializer);
        registerRequestHandler(BalanceRequest.ID, (BalanceRequest req, short source1) -> uponBalanceRequest(source1));
        registerRequestHandler(SendTransactionRequest.ID, this::uponSendTransactionRequest);
        subscribeNotification(DeliverFinalizedBlocksContentNotification.ID, (DeliverFinalizedBlocksContentNotification notif, short source) -> uponDeliverFinalizedBlocksContentNotification(notif));
    }

    /**
     * Should I be the original node in the application.
     * All coins belong to me.
     * @param props Contains the information of whether I am the original node.
     */
    private void loadInitialUTXOs(Properties props) throws Exception {
        PublicKey original = CryptographicUtils.readECDSAPublicKey(props.getProperty("originalPublic"));
        if (self.equals(original)) {
            int numberCoins = parseInt(props.getProperty("numberCoins",
                    String.valueOf(MempoolManager.NUMBER_COINS)));
            IntStream.range(0, numberCoins).forEach(i -> myUTXOs.put(new UUID(0, i), 1));
        }
    }

    @Override
    public void init(Properties properties) {}

    private void uponBalanceRequest(short source) {
        sendReply(new BalanceReply(countMyCoins()), source);
    }

    private void uponSendTransactionRequest(SendTransactionRequest req, short source) {
        boolean successful = false;
        PublicKey txDestination = req.getTransactionDestination();
        int txAmount = req.getTransactionAmount();
        try {
            SlimTransaction tx = generateTransaction(txDestination, txAmount);
            sendRequest(new DisseminateTransactionRequest(tx), ValueDispatcher.ID);
            successful = true;
        } catch (Exception e) {
            logger.info("Could not generate transaction because of exception: '{}'", e.getMessage());
        }
        sendReply(new SendTransactionReply(successful, txDestination, txAmount), source);
    }

    //pre: !nodes.isEmpty() && myCoins > 1
    public SlimTransaction generateTransaction(PublicKey destination, int amount) throws IOException,
            SignatureException, InvalidKeyException, NotEnoughCoinsException {
        var inputEntries = getInputUTXOS(amount);
        int inputCoins = inputEntries.stream().mapToInt(Map.Entry::getValue).sum();
        List<UUID> input = inputEntries.stream().map(Map.Entry::getKey).collect(Collectors.toUnmodifiableList());
        List<Integer> destinationAmount = List.of(amount);
        List<Integer> originAmount = inputCoins - amount == 0 ?
                Collections.emptyList() : List.of(inputCoins - amount);
        var tx = new SlimTransaction(self, destination, input, destinationAmount, originAmount, signer);
        input.forEach(myUTXOs::remove);
        return tx;
    }

    public int countMyCoins() {
        return myUTXOs.values().stream().mapToInt(i -> i).sum();
    }

    private List<Map.Entry<UUID, Integer>> getInputUTXOS(int cost) throws NotEnoughCoinsException {
        int remaining = cost;
        List<Map.Entry<UUID, Integer>> input = new LinkedList<>();
        Iterator<Map.Entry<UUID, Integer>> myUTXOIt = myUTXOs.entrySet().iterator();
        while (remaining > 0 && myUTXOIt.hasNext()) {
            var utxo = myUTXOIt.next();
            remaining -= utxo.getValue();
            input.add(utxo);
        }
        if (remaining > 0)
            throw new NotEnoughCoinsException(input.stream().mapToInt(Map.Entry::getValue).sum(), cost);
        return input;
    }

    public static UUID genIDFromKey(PublicKey key) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(CryptographicUtils.HASH_ALGORITHM);
        byte[] keyContents = md.digest(key.getEncoded());
        try (var in = new DataInputStream(new ByteArrayInputStream(keyContents))) {
            return new UUID(in.readLong(), in.readLong());
        }
    }

    //Records the UTXOs targeted at this node.
    private void uponDeliverFinalizedBlocksContentNotification(
            DeliverFinalizedBlocksContentNotification notif) {
        updateUtxos(notif.getRemovedUtxo(), notif.getAddedUtxos());
    }

    public void updateUtxos(Set<StorageUTXO> removed, Set<StorageUTXO> added) {
        added.parallelStream()
                .filter(utxo -> utxo.getUTXOOwner().equals(self))
                .forEachOrdered(utxo -> myUTXOs.put(utxo.getId(), utxo.getAmount()));
        removed.stream()
                .map(StorageUTXO::getId)
                .forEach(myUTXOs::remove);
    }

}
