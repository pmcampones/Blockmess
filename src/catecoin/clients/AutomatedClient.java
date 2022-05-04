package catecoin.clients;

import catecoin.blocks.SimpleBlockContentList;
import catecoin.transactionGenerators.TransactionGenerator;
import catecoin.nodeJoins.AutomatedNodeJoin;
import catecoin.notifications.DeliverFinalizedBlockIdentifiersNotification;
import catecoin.replies.BalanceReply;
import catecoin.requests.BalanceRequest;
import catecoin.requests.SendTransactionRequest;
import catecoin.timers.AnnounceNodeTimer;
import catecoin.timers.GenerateTransactionTimer;
import catecoin.txs.SlimTransaction;
import com.google.common.collect.Sets;
import ledger.blocks.LedgerBlockImp;
import main.ProtoPojo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;
import utils.IDGenerator;
import valueDispatcher.ValueDispatcher;
import valueDispatcher.notifications.DeliverAutomatedNodeJoinNotification;
import valueDispatcher.requests.DisseminateAutomatedNodeJoinRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.*;

import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
import static java.nio.file.StandardOpenOption.APPEND;

public class AutomatedClient extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(AutomatedClient.class);

    private static final float MAX_AMOUNT_PER_TRANSACTION = 0.1f; //10% of myCoins

    private static final int TRANSACTION_PERIOD = 1000 / 2; //0.5 seconds

    private static final int NODE_ANNOUNCEMENT_PERIOD = 30 * 1000; //30 secs

    private static final String AUTOMATED_OUTPUT_LOG_FILE = "./log_node.txt";

    public static final short ID = IDGenerator.genId();

    private final PublicKey self;

    private final float maxAmountPerTransaction;

    private final int transactionPeriod;

    private final int nodeAnnouncementPeriod;

    private final String automatedOutputLogFile;

    //Must be ArrayList instead of List because the collection must implement the RandomAccess interface
    private final Set<PublicKey> nodes = Sets.newConcurrentHashSet();

    private boolean canSendTransactions = true;

    public AutomatedClient(Properties props, PublicKey self) throws Exception {
        super(AutomatedClient.class.getSimpleName(), ID);
        this.self = self;
        this.maxAmountPerTransaction = parseFloat(props.getProperty("maxAmountPerTransaction",
                String.valueOf(MAX_AMOUNT_PER_TRANSACTION)));
        this.transactionPeriod = parseInt(props.getProperty("transactionPeriod",
                String.valueOf(TRANSACTION_PERIOD)));
        this.nodeAnnouncementPeriod = parseInt(props.getProperty("nodeAnnouncementPeriod",
                String.valueOf(NODE_ANNOUNCEMENT_PERIOD)));
        this.automatedOutputLogFile = generateLogFile(props);
        registerTimers();
        registerReplyHandler(BalanceReply.ID, (BalanceReply reply, short source) -> uponBalanceReply(reply));
        subscribeNotifications();
        registerPojosSerializers();
    }

    private String generateLogFile(Properties props) throws IOException {
        String automatedOutputLogFile = props.getProperty("automatedOutputLogFile",
                AUTOMATED_OUTPUT_LOG_FILE);
        Path filePath = Path.of(automatedOutputLogFile);
        Files.deleteIfExists(filePath);
        Files.createDirectories(filePath.getParent());
        Files.createFile(filePath);
        return automatedOutputLogFile;
    }

    private void registerTimers() throws HandlerRegistrationException {
        registerTimerHandler(GenerateTransactionTimer.ID, (GenerateTransactionTimer timer1, long id1) -> uponGenerateTransactionTimer());
        registerTimerHandler(AnnounceNodeTimer.ID, (AnnounceNodeTimer timer, long id) -> uponAnnounceNodeTimer());
    }

    private void subscribeNotifications() throws HandlerRegistrationException {
        subscribeNotification(DeliverAutomatedNodeJoinNotification.ID,
                (DeliverAutomatedNodeJoinNotification notif1, short source1) -> uponDeliverAutomatedNodeJoinNotification(notif1));
        subscribeNotification(DeliverFinalizedBlockIdentifiersNotification.ID,
                (DeliverFinalizedBlockIdentifiersNotification notif, short source) -> uponDeliverFinalizedBlockNotification(notif));
    }

    private void registerPojosSerializers() {
        ProtoPojo.pojoSerializers.put(AutomatedNodeJoin.ID, AutomatedNodeJoin.serializer);
        ProtoPojo.pojoSerializers.put(SlimTransaction.ID, SlimTransaction.serializer);
        ProtoPojo.pojoSerializers.put(PoETDRandProof.ID, PoETDRandProof.serializer);
        ProtoPojo.pojoSerializers.put(LedgerBlockImp.ID, LedgerBlockImp.serializer);
        ProtoPojo.pojoSerializers.put(SimpleBlockContentList.ID, SimpleBlockContentList.serializer);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        if (props.getProperty("simplifiedTxs", "F").equals("F")) {
            setupPeriodicTimer(new GenerateTransactionTimer(),
                    transactionPeriod, transactionPeriod);
            setupPeriodicTimer(new AnnounceNodeTimer(),
                    nodeAnnouncementPeriod, nodeAnnouncementPeriod);
        }
    }

    private void uponGenerateTransactionTimer() {
        if (canSendTransactions)
            sendRequest(new BalanceRequest(), TransactionGenerator.ID);
    }

    private void uponAnnounceNodeTimer() {
        AutomatedNodeJoin nodeJoin = new AutomatedNodeJoin(self);
        sendRequest(new DisseminateAutomatedNodeJoinRequest(nodeJoin), ValueDispatcher.ID);
    }

    private void uponBalanceReply(BalanceReply reply) {
        int nodeBalance = reply.getBalance();
        if (nodeBalance > 1) {
            sendTransaction(nodeBalance);
        } else if (nodeBalance >= 0) {
            logger.debug("Unable to generate transaction because I have no coins.");
            canSendTransactions = false;
        } else {
            logger.error("Somehow managed to attain a negative balance of: {}", nodeBalance);
            canSendTransactions = false;
        }
    }

    private void sendTransaction(int nodeBalance) {
        Optional<PublicKey> destination = getDestination();
        if (destination.isPresent()) {
            Random r = new Random();
            int amount = (int) Math.ceil(r.nextFloat() * maxAmountPerTransaction * nodeBalance);
            logger.debug("Sending transaction of amount {}, from balance {}, to destination {}",
                    amount, nodeBalance, destination.get());
            sendRequest(new SendTransactionRequest(destination.get(), amount), TransactionGenerator.ID);
        } else {
            logger.debug("Unable to generate transaction because I know no other nodes.");
        }
    }

    private Optional<PublicKey> getDestination() {
        if (nodes.isEmpty())
            return Optional.empty();
        List<PublicKey> nodesList = new ArrayList<>(nodes);
        int randomIndex = new Random().nextInt(nodesList.size());
        return Optional.of(nodesList.get(randomIndex));
    }

    private void uponDeliverAutomatedNodeJoinNotification(DeliverAutomatedNodeJoinNotification notif) {
        AutomatedNodeJoin nodeJoin = notif.getAutomatedNodeJoin();
        PublicKey nodeKey = nodeJoin.getKey();
        if (!nodeKey.equals(self))
            nodes.add(nodeKey);
    }

    private void uponDeliverFinalizedBlockNotification(DeliverFinalizedBlockIdentifiersNotification notif) {
        try {
            tryToLogFinalizedBlocks(notif.getFinalizedBlocksIds());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void tryToLogFinalizedBlocks(List<UUID> finalized) throws IOException {
        Path filepath = Path.of(automatedOutputLogFile);
        for (UUID block : finalized) {
            logger.info("Received finalized block {}.",block);
            Files.writeString(filepath, block.toString() + "\n", APPEND);
        }
        this.canSendTransactions = true;
    }
}
