package broadcastProtocols.lazyPush;

import broadcastProtocols.BroadcastProtocol;
import broadcastProtocols.PeriodicPrunableHashMap;
import broadcastProtocols.StateRecoveryBroadcastModule;
import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import broadcastProtocols.lazyPush.messages.LazyValMessage;
import broadcastProtocols.lazyPush.messages.RequestValMessage;
import broadcastProtocols.lazyPush.messages.ValIdentifierMessage;
import broadcastProtocols.lazyPush.requests.LazyBroadcastRequest;
import broadcastProtocols.lazyPush.timers.DelayedResponsesTimer;
import broadcastProtocols.notifications.DeliverVal;
import broadcastProtocols.notifications.PeerUnreachableNotification;
import catecoin.notifications.AnswerMessageValidationNotification;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peerSamplingProtocols.hyparview.notifications.NeighbourDownNotification;
import peerSamplingProtocols.hyparview.notifications.NeighbourUpNotification;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.babel.handlers.MessageFailedHandler;
import pt.unl.fct.di.novasys.babel.handlers.MessageInHandler;
import pt.unl.fct.di.novasys.babel.handlers.NotificationHandler;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.IDGenerator;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;

public class LazyPushBroadcast extends GenericProtocol implements BroadcastProtocol {

    private static final Logger logger = LogManager.getLogger(LazyPushBroadcast.class);

    public static final short ID = IDGenerator.genId();

    private static final long DELAYED_VALUE_TIMER = 1000; //Milliseconds

    private static final int PORT_OFFSET = 1000;

    /**
     * Underlying peer sampling protocol.
     */
    //private final PeerSamplingProtocol membership;

    private final Host self;

    private final Map<UUID, LazyValMessage> messageBuffer;

    private final long delayedValueTimer;

    private final int channelId;

    private final Set<Host> peers = Sets.newConcurrentHashSet();

    /**
     * Identifiers of the messages whose content this node is waiting for, mapped to the peers that have the message content.
     */
    private final Map<UUID, Collection<Host>> messageOwners = new ConcurrentHashMap<>();

    /**
     * Identifiers of messages whose content this node is waiting for, mapped to the time of the request.
     */
    private final Map<UUID, Long> waitingForContent = new ConcurrentHashMap<>();

    /**
     * Maps the identifiers of objects that block in the broadcast
     * (see {@link BlockingBroadcast})
     *  to the identifiers of the messages that encapsulated them.
     *  <p>This is used to resume the broadcast of an object after it has been successfully validated.</p>
     */
    private final Map<UUID, UUID> mapBlockingIdToMid = new ConcurrentHashMap<>();

    /**
     * Disseminates messages using a lazy push approach.
     * <p>The following three steps comprise the logic behind the transmission of messages from a node A to B.</p>
     * <p>&emsp 1st -> node A receives the contents of a value proposed by the application and notifies its peers sending them the value identifier</p>
     * <p>&emsp 2nd -> node B receives the identifier and checks if it has a value with the same identifier. If not, requests the value from A
     * <p>&emsp 3rd -> node A receives the request from B and sends it the value.</p>
     * <p>Upon the conclusion of these steps, node B will repeat the process from step 1 to transmit the information to some other node C.</p>
     * <p>This execution occurs for every value proposed until there are no nodes unaware of the value proposed.</p>
     **/
    public LazyPushBroadcast(Properties props, Host self,
                             PeriodicPrunableHashMap<UUID, LazyValMessage> messageBuffer)
            throws HandlerRegistrationException, IOException {
        super(LazyPushBroadcast.class.getSimpleName(), ID);
        this.self = self;
        //this.membership = membership;
        this.messageBuffer = messageBuffer;
        this.delayedValueTimer = parseLong(props.getProperty("delayedValueTimer",
                String.valueOf(DELAYED_VALUE_TIMER)));
        channelId = createTCPChannel(props);
        registerRequestHandler(LazyBroadcastRequest.ID, (LazyBroadcastRequest request, short sourceProto) -> uponBroadcastRequest(request));
        registerTimerHandler(DelayedResponsesTimer.ID, (DelayedResponsesTimer t, long timerId) -> uponDelayedResponseTimer());
        subscribeNotifications();
        registerMessageConfigs();
        registerRecoveryMechanism(props);
    }

    private int createTCPChannel(Properties props) throws IOException {
        // Create a properties object to setup channel-specific properties. See the
        // channel description for more details.
        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, props.getProperty("address")); // The address to bind to
        int port = Integer.parseInt(props.getProperty("port")) + PORT_OFFSET;
        channelProps.setProperty(TCPChannel.PORT_KEY, String.valueOf(port)); // The port to bind to
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); // Heartbeats interval for established
        // connections
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); // Time passed without heartbeats until
        // closing a connection
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); // TCP connect timeout
        int channelId = createChannel(TCPChannel.NAME, channelProps);
        logger.info("Using channel {} for the broadcast protocol.", channelId);
        return channelId; // Create the channel with the given properties
    }

    private void subscribeNotifications() throws HandlerRegistrationException {
        subscribeNotification(AnswerMessageValidationNotification.ID,
                (AnswerMessageValidationNotification notif2, short source2) -> uponAnswerMessageValidationNotification(notif2));
        subscribeNotification(NeighbourUpNotification.ID, (NeighbourUpNotification notif1, short source1) -> uponNeighbourUpNotification(notif1));
        subscribeNotification(NeighbourDownNotification.ID, (NeighbourDownNotification notif, short source) -> uponNeighbourDownNotification(notif));
    }

    private void registerMessageConfigs() throws HandlerRegistrationException {
        registerMessageSerializers();
        registerMessageHandlers();
    }

    private void registerMessageSerializers() {
        registerMessageSerializer(channelId, LazyValMessage.ID, LazyValMessage.serializer);
        registerMessageSerializer(channelId, ValIdentifierMessage.ID, ValIdentifierMessage.serializer);
        registerMessageSerializer(channelId, RequestValMessage.ID, RequestValMessage.serializer);
    }

    private void registerMessageHandlers() throws HandlerRegistrationException {
        registerMessageHandler(channelId, LazyValMessage.ID,
                (LazyValMessage msg2, Host from2, short sourceProto2, int channelId3) -> uponLazyValMessage(msg2, from2), (msg3, to, destProto, throwable, channelId4) -> uponMsgFail(msg3, to, throwable));
        registerMessageHandler(channelId, ValIdentifierMessage.ID,
                (ValIdentifierMessage msg1, Host from1, short sourceProto1, int channelId2) -> uponValIdentifierMessage(msg1, from1), (msg2, to, destProto, throwable, channelId3) -> uponMsgFail(msg2, to, throwable));
        registerMessageHandler(channelId, RequestValMessage.ID,
                (RequestValMessage msg, Host from, short sourceProto, int channelId1) -> uponRequestValMessage(msg, from, channelId1), (msg1, to, destProto, throwable, channelId2) -> uponMsgFail(msg1, to, throwable));
    }

    private void registerRecoveryMechanism(Properties props)
            throws HandlerRegistrationException {
        String useRecovery = props.getProperty("lazyBroadcastStateRecovery", "T");
        if (useRecovery.equals("T")) {
            logger.info("Lazy push broadcast protocol initialized with a state recovery mechanism");
            StateRecoveryBroadcastModule recoveryBroadcastModule =
                    new StateRecoveryBroadcastModule(this);
            registerRecoveryMessageHandlers(recoveryBroadcastModule.getMessageHandlers());
            registerRecoveryMessageSerializers(StateRecoveryBroadcastModule.getSerializers());
            subscribeRecoveryNotifications(recoveryBroadcastModule.getNotificationSubscriptions());
        } else {
            logger.info("Lazy push broadcast protocol initialized without a state recovery mechanism.");
        }
    }

    private void registerRecoveryMessageHandlers(
            List<Triple<Short, MessageInHandler<ProtoMessage>,
                    MessageFailedHandler<ProtoMessage>>> handlers)
            throws HandlerRegistrationException {
        for (var mH : handlers) {
            registerMessageHandler(channelId, mH.getLeft(), mH.getMiddle(), mH.getRight());
        }
    }

    private void registerRecoveryMessageSerializers(
            List<Pair<Short, ISerializer<? extends ProtoMessage>>> serializers) {
        for (var mS : serializers) {
            registerMessageSerializer(channelId, mS.getLeft(), mS.getRight());
        }
    }

    private void subscribeRecoveryNotifications(
            List<Pair<Short, NotificationHandler<? extends ProtoNotification>>> subscriptions)
            throws HandlerRegistrationException {
        for (var nS : subscriptions) {
            subscribeNotification(nS.getLeft(), nS.getRight());
        }
    }

    @Override
    public void init(Properties props) {}

    private void uponBroadcastRequest(LazyBroadcastRequest request) {
        logger.info("Broadcast request to {} of value: {}", self, request.getVal());
        LazyValMessage message = new LazyValMessage(UUID.randomUUID(), request.getVal());
        uponLazyValMessage(message, self);
    }

    private void uponMsgFail(ProtoMessage msg, Host to,
                             Throwable throwable) {
        logger.error("Message {} to {} failed, reason: {}\n" +
                "Notifying peer sampling that this node is unreliable.", msg, to, throwable);
        triggerNotification(new PeerUnreachableNotification());
    }

    /**
     * This node records a received message 'msg',
     * delivers it to the application,
     * and notifies all its peers of the message's identifier
     */
    private void uponLazyValMessage(
            LazyValMessage msg, Host from) {
        UUID mid = msg.getMid();
        if (!messageBuffer.containsKey(mid)) {
            logger.info("Received message: {}\n From {}",
                    msg.toString(), from);
            messageBuffer.put(mid, msg);
            waitingForContent.remove(mid);
            if (msg.isBlocking()) {
                logger.debug("Received message {} is blocking. Waiting validation.", mid);
                recordValueToBeValidated(msg);
            } else {
                disseminateMessage(mid);
            }
            //deliverLazyValMessage(msg);
            triggerNotification(new DeliverVal(msg.getVal()));
        }
    }

    private void recordValueToBeValidated(LazyValMessage msg) {
        try {
            UUID mid = msg.getMid();
            UUID blockingId = msg.getBlockingID();
            mapBlockingIdToMid.put(blockingId, mid);
        } catch (InnerValueIsNotBlockingBroadcast innerValueIsNotBlockingBroadcast) {
            innerValueIsNotBlockingBroadcast.printStackTrace();
        }
    }

    private void uponAnswerMessageValidationNotification(
            AnswerMessageValidationNotification notif) {
        UUID mid = mapBlockingIdToMid.get(notif.getBlockingMessageID());
        if (mid != null) {
            logger.debug("Continuing the dissemination of message {}.", mid);
            disseminateMessage(mid);
        }
    }

    private void disseminateMessage(UUID mid) {;
        logger.debug("Sending identifier {} to peers: {}", mid, peers);
        for (Host dest : peers)
            sendMessage(channelId, new ValIdentifierMessage(mid), dest);
    }

    /**
     * Receives the identifier of a message from a peer and if it is new, request it.
     * <p>As the owner of the message may fail and others also have the requested value,
     * all peers that sent the identifier are recorded.</p>
     */
    private void uponValIdentifierMessage(ValIdentifierMessage msg, Host from) {
        uponValIdentifier(msg.getIdOfTheContentMessage(), from);
    }

    private void uponValIdentifier(UUID mid, Host from) {
        logger.debug("Received identifier {} form peer {}", mid, from);
        if (!messageBuffer.containsKey(mid)) {
            if (waitingForContent.containsKey(mid))
                messageOwners.get(mid).add(from);
            else {
                waitingForContent.put(mid, System.currentTimeMillis());
                messageOwners.put(mid, new LinkedList<>());
                logger.debug("Sending request message of {} to {}", mid, from);
                sendMessage(channelId, new RequestValMessage(mid), from);
            }
        }
    }

    /**
     *A peer requests a value this node holds, simply sent it.
     */
    private void uponRequestValMessage(RequestValMessage msg, Host from, int channelId) {
        UUID mid = msg.getMissingMessageId();
        logger.debug("Peer {} requested value of identifier {}", from, mid);
        LazyValMessage valM = messageBuffer.get(mid);
        if (valM != null) {
            logger.debug("I have the content requested by {}, sending {} content", from, mid);
            sendMessage(channelId, valM, from);
        }
    }

    /**
     * If a request for a value is taking too long, we ask other peers for the value
     */
    private void uponDelayedResponseTimer() {
        Collection<UUID> delayedResponses = waitingForContent.entrySet().stream()
                .filter(e -> System.currentTimeMillis() - e.getValue() < delayedValueTimer)
                .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
        for (UUID mid : delayedResponses) {
            waitingForContent.remove(mid);
            Collection<Host> holders = messageOwners.remove(mid);
            if (holders != null)
                holders.forEach(h -> uponValIdentifier(mid, h));
        }
    }

    private void uponNeighbourUpNotification(NeighbourUpNotification notif) {
        Host peerOg = notif.getNeighbour();
        Host peerModified = new Host(peerOg.getAddress(), peerOg.getPort() + PORT_OFFSET);
        logger.debug("Opening connection to node: {}", peerModified);
        openConnection(peerModified, channelId);
        peers.add(peerModified);
    }

    private void uponNeighbourDownNotification(NeighbourDownNotification notif) {
        Host peerOg = notif.getNeighbour();
        Host peerModified = new Host(peerOg.getAddress(), peerOg.getPort() + PORT_OFFSET);
        logger.debug("Closing connection with node: {}", peerModified);
        peers.remove(peerModified);
        closeConnection(peerModified, channelId);
    }

    @Override
    public Set<UUID> getMsgIds() {
        return messageBuffer.keySet();
    }

    @Override
    public Set<ProtoMessage> getMsgs() {
        return Set.copyOf(messageBuffer.values());
    }

    @Override
    public Set<Host> getPeers() {
        return peers;
    }

    @Override
    public void sendMessageToPeer(ProtoMessage msg, Host target) {
        sendMessage(channelId, msg, target);
    }

    @Override
    public void deliverMessage(ProtoMessage msg) {
        if (msg instanceof LazyValMessage) {
            deliverLazyValMessage((LazyValMessage) msg);
        } else {
            logger.error("Attempted to deliver state recovery message," +
                            " but it was not of type: {}",
                    LazyValMessage.class.getSimpleName());
        }
    }

    private void deliverLazyValMessage(LazyValMessage msg) {
        UUID mid = msg.getMid();
        messageBuffer.put(mid, msg);
        waitingForContent.remove(mid);
        triggerNotification(new DeliverVal(msg.getVal()));
    }
}
