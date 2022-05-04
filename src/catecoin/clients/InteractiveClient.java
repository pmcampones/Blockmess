package catecoin.clients;

import catecoin.blocks.SimpleBlockContentList;
import catecoin.exceptions.AccountDoesNotExistException;
import catecoin.nodeJoins.InteractiveNodeJoin;
import catecoin.notifications.DeliverFinalizedBlocksContentNotification;
import catecoin.replies.BalanceReply;
import catecoin.replies.SendTransactionReply;
import catecoin.requests.BalanceRequest;
import catecoin.requests.SendTransactionRequest;
import catecoin.timers.AnnounceNodeTimer;
import catecoin.transactionGenerators.TransactionGenerator;
import catecoin.txs.SlimTransaction;
import chatApp.ChatMessage;
import ledger.blocks.LedgerBlockImp;
import main.ProtoPojo;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;
import utils.IDGenerator;
import valueDispatcher.ValueDispatcher;
import valueDispatcher.notifications.DeliverChatMessageNotification;
import valueDispatcher.notifications.DeliverInteractiveNodeJoinNotification;
import valueDispatcher.requests.DisseminateChatMessageRequest;
import valueDispatcher.requests.DisseminateInteractiveNodeJoinRequest;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Interactive client used to interact with the application.
 * Can create transactions and is notified when state is modified.
 */
public class InteractiveClient extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(InteractiveClient.class);

    private static final long ANNOUNCEMENT_PERIOD = 300000; //5 mins

    public static final short ID = IDGenerator.genId();

    public final String username;

    public final long announcementPeriod;

    private enum Command {
        HELP("Help", "Description of the commands"),
        BALANCE("Balance", "Retrieves this account's balance.\n" +
                "\tThe Balance accounts only with un-submitted UTXOs.\n" +
                "\tEven before a Transaction is confirmed, its funds are deducted from the account.\n" +
                "\tDue to the nature of the UTXO model, upon paying an X amount, more than X coins may be deducted. " +
                "The difference will eventually be reimbursed."),
        FIND_USER("Get Username", "Retrieves the account identifiers of users with a given username."),
        SEND_TX_TO("Send", "Sends a transaction to a given account.\n" +
                "\tThe transaction requires an account unique identifier or a username, as long as this is unique.\n" +
                "\tAn amount must be set for the transaction; this must not exceed that which is obtained from the Balance command.\n" +
                "\tA notification will eventually appear when the system has confirmed the transaction."),
        FIND_MATCH("Match Username", "Find the usernames matching a regex."),
        ALL_USERS("All Users", "Retrieves a list with all known users in the system."),
        EXIT("Exit", "Leave the program. All submitted transactions will still be confirmed with high probability."),
        CHAT("Chat", "Broadcast message."); //Merely for debug purposes.
        /*NODE_JOIN("Join", "Broadcast ourselves");*/

        private final String invocation;

        private final String helpMessage;

        Command(String invocation, String helpMessage) {
            this.invocation = invocation;
            this.helpMessage = helpMessage;
        }

        static Command getFromInvocation(String userInv) throws NoSuchElementException {
            return Arrays.stream(values())
                    .filter(c -> c.invocation.equalsIgnoreCase(userInv))
                    .iterator().next();
        }
    }

    private final PublicKey self;

    private final Map<String, Set<UUID>> users = new HashMap<>();

    private final BidiMap<UUID, PublicKey> accounts = new DualHashBidiMap<>();

    public InteractiveClient(Properties props, PublicKey self) throws HandlerRegistrationException {
        super(InteractiveClient.class.getSimpleName(), ID);
        this.self = self;
        username = props.getProperty("username", "Pablo" + new Random().nextInt());
        announcementPeriod = Long.parseLong(
                props.getProperty("announcementPeriod", String.valueOf(ANNOUNCEMENT_PERIOD)));

        registerReplyHandlers();
        subscribeNotifications();
        registerPojosSerializers();
        registerTimerHandler(AnnounceNodeTimer.ID, (AnnounceNodeTimer timer, long id) -> uponAnnounceNodeTimer());
    }

    private void registerReplyHandlers() throws HandlerRegistrationException {
        registerReplyHandler(BalanceReply.ID, this::uponBalanceReply);
        registerReplyHandler(SendTransactionReply.ID, (SendTransactionReply reply, short source) -> uponSendTransactionReply(reply));
    }

    private void subscribeNotifications() throws HandlerRegistrationException {
        subscribeNotification(DeliverFinalizedBlocksContentNotification.ID, (DeliverFinalizedBlocksContentNotification notif1, short source1) -> uponNewApplicationContentNotification(notif1));
        subscribeNotification(DeliverInteractiveNodeJoinNotification.ID, (DeliverInteractiveNodeJoinNotification notif1, short source1) -> uponDeliverNodeJoinNotification(notif1));
        subscribeNotification(DeliverChatMessageNotification.ID, (DeliverChatMessageNotification notif, short source) -> uponDeliverChatMessageNotification(notif));
    }

    private void registerPojosSerializers() {
        ProtoPojo.pojoSerializers.put(InteractiveNodeJoin.ID, InteractiveNodeJoin.serializer);
        ProtoPojo.pojoSerializers.put(SlimTransaction.ID, SlimTransaction.serializer);
        ProtoPojo.pojoSerializers.put(PoETDRandProof.ID, PoETDRandProof.serializer);
        ProtoPojo.pojoSerializers.put(LedgerBlockImp.ID, LedgerBlockImp.serializer);
        ProtoPojo.pojoSerializers.put(SimpleBlockContentList.ID, SimpleBlockContentList.serializer);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        announceMyself();
        long period = Long.parseLong(props.getProperty("announcementperiod", String.valueOf(ANNOUNCEMENT_PERIOD)));
        setupPeriodicTimer(new AnnounceNodeTimer(), period, period);
        new Thread(this::execute).start();
    }

    private void uponAnnounceNodeTimer() {
        announceMyself();
    }

    private void announceMyself() {
        logger.info("Announcing myself");
        InteractiveNodeJoin nodeJoin = new InteractiveNodeJoin(self, username);
        sendRequest(new DisseminateInteractiveNodeJoinRequest(nodeJoin), ValueDispatcher.ID);
    }

    public void execute() {
        try (Scanner in = new Scanner(System.in)) {readUserCommands(in);}
    }

    private void readUserCommands(Scanner in) {
        Command command;
        do {
            try {
                command = Command.getFromInvocation(in.nextLine());
                executeCommand(command, in);
            } catch (NoSuchElementException e) {
                System.out.println("There is no such command. The available commands are:");
                Arrays.stream(Command.values()).map(c -> c.invocation).forEach(System.out::println);
                command = Command.HELP;
            }
        } while (!command.equals(Command.EXIT));

    }

    private void executeCommand(Command c, Scanner in) {
        switch (c) {
            case HELP:
                executeHelp();
                break;
            case BALANCE:
                executeBalance();
                break;
            case FIND_USER:
                executeFindUser(in);
                break;
            case SEND_TX_TO:
                executeSendTxTo(in);
                break;
            case FIND_MATCH:
                executeFindMatch(in);
                break;
            case ALL_USERS:
                executeAllUsers();
                break;
            case CHAT:
                executeChatMessage(in);
                break;
            case EXIT:
                System.out.println("Goodbye :)");
                break;
        }
    }

    private void executeHelp() {
        logger.info("User issued a help command.");
        Arrays.stream(Command.values()).map(c -> String.format("%s:\n\t%s", c.invocation, c.helpMessage))
                .forEach(System.out::println);
    }

    private void executeBalance() {
        logger.info("User issued a balance request.");
        sendRequest(new BalanceRequest(), TransactionGenerator.ID);
    }

    private void uponBalanceReply(BalanceReply reply, short source) {
        logger.info("Balance request answered from {}", source);
        System.out.println("This account's balance is: " + reply.getBalance());
    }

    private void executeFindUser(Scanner in) {
        System.out.print("Insert username: ");
        String username = in.nextLine();
        logger.info("User attempted to identify user '{}' accounts.", username);
        System.out.println("Accounts associated with this username are:\n" + users.get(username));
    }

    private void executeSendTxTo(Scanner in) {
        try {
            tryToSendTxTo(in);
        } catch (AccountDoesNotExistException e) {
            System.out.println(e.getMessage());
        } catch (Exception e) {
            System.out.println("Malformed input. Command ignored.");
        }
    }

    private void tryToSendTxTo(Scanner in) throws RuntimeException, AccountDoesNotExistException {
        System.out.print("To Which account: ");
        UUID destination = UUID.fromString(in.nextLine());
        if (!accounts.containsKey(destination))
            throw new AccountDoesNotExistException(destination);
        System.out.print("How much: ");
        int amount = in.nextInt();
        in.nextLine();
        System.out.println("Submitting transaction.");
        logger.info("Sending transaction to {} of amount {}.", destination, amount);
        sendRequest(new SendTransactionRequest(accounts.get(destination), amount), TransactionGenerator.ID);
    }

    private void uponSendTransactionReply(SendTransactionReply reply) {
        String stub = reply.wasTheTransactionSuccessfullySent()
                ? "Successfully submitted " : "Failed to submit ";
        PublicKey txDest = reply.getTransactionDestination();
        int txAmount = reply.getTransactionAmount();
        logger.info("Received transaction ack with destination {} of amount {}.", txDest, txAmount);
        System.out.printf("%s transaction to %s of value %d\n", stub, txDest, txAmount);
    }

    private void executeFindMatch(Scanner in) {
        System.out.println("Insert username pattern: ");
        String match = in.nextLine();
        Pattern pattern = Pattern.compile(match);
        List<String> matches = users.keySet().stream()
                .filter(k -> pattern.matcher(k).find())
                .collect(Collectors.toList());
        System.out.println("Usernames matching:\n" + matches);
    }

    private void executeAllUsers() {
        users.entrySet().forEach(System.out::println);
    }

    private void executeChatMessage(Scanner in) {
        String message = in.nextLine();
        sendRequest(new DisseminateChatMessageRequest(new ChatMessage(message)), ValueDispatcher.ID);
    }

    private void uponDeliverNodeJoinNotification(DeliverInteractiveNodeJoinNotification notif) {
        try {
            InteractiveNodeJoin interactiveNodeJoin = notif.getNodeJoin();
            PublicKey nodeKey = interactiveNodeJoin.getNodeKey();
            UUID nodeId = TransactionGenerator.genIDFromKey(nodeKey);
            accounts.put(nodeId, nodeKey);
            String username = interactiveNodeJoin.getUsername();
            Set<UUID> ofUsername = users.get(username);
            logger.info("Notified that node {} with username '{}' joined the system.",
                    nodeId, username);
            if (ofUsername == null)
                users.put(username, new HashSet<>(Collections.singleton(nodeId)));
            else ofUsername.add(nodeId);
        }catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();    //These should never happen
        }
    }

    private void uponNewApplicationContentNotification(DeliverFinalizedBlocksContentNotification notif) {
        notif.getAddedUtxos().parallelStream()
                .filter(utxo -> utxo.getUTXOOwner().equals(self))
                .forEach(u -> System.out.printf("Received %d coins.\n", u.getAmount()));
    }

    private void uponDeliverChatMessageNotification(DeliverChatMessageNotification notif) {
        System.out.println(notif.getChatMessage().getMessage());
    }

}
