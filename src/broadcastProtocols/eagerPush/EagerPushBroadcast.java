package broadcastProtocols.eagerPush;

import broadcastProtocols.BroadcastProtocol;
import broadcastProtocols.PeriodicPrunableHashMap;
import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import broadcastProtocols.notifications.DeliverVal;
import broadcastProtocols.notifications.PeerUnreachableNotification;
import com.google.common.collect.Sets;
import main.GlobalProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peerSamplingProtocols.hyparview.notifications.NeighbourDownNotification;
import peerSamplingProtocols.hyparview.notifications.NeighbourUpNotification;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.IDGenerator;
import validators.AnswerMessageValidationNotification;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EagerPushBroadcast extends GenericProtocol implements BroadcastProtocol {

    private static final Logger logger = LogManager.getLogger(EagerPushBroadcast.class);

    public static final short ID = IDGenerator.genId();

    public static final int PORT_OFFSET = 2000;

    private final int channelId;

    private final Set<Host> peers = Sets.newConcurrentHashSet();

    private final Map<UUID, EagerValMessage> messageBuffer;

    /**
     * Maps the identifiers of objects that block in the broadcast
     *  to the identifiers of the messages that encapsulated them.
     *  <p>This is used to resume the broadcast of an object after it has been successfully validated.</p>
     */
    private final Map<UUID, EagerValMessage> mapBlockingIdToMid = new ConcurrentHashMap<>();

    public EagerPushBroadcast()
            throws HandlerRegistrationException, IOException {
        super(EagerPushBroadcast.class.getSimpleName(), ID);
        this.messageBuffer = new PeriodicPrunableHashMap<>();
        channelId = createTCPChannel();
        subscribeNotifications();
        registerRequestHandler(EagerBroadcastRequest.ID, this::uponBroadcastRequest);
        registerMessageConfigs();
    }

    private void subscribeNotifications() throws HandlerRegistrationException {
        subscribeNotification(AnswerMessageValidationNotification.ID,
                (AnswerMessageValidationNotification notif, short source) -> uponAnswerMessageValidationNotification(notif));
        subscribeNotification(NeighbourUpNotification.ID, (NeighbourUpNotification notif, short source) -> uponNeighbourUpNotification(notif));
        subscribeNotification(NeighbourDownNotification.ID, (NeighbourDownNotification notif, short source) -> uponNeighbourDownNotification(notif));
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

    private int createTCPChannel() throws IOException {
        Properties props = GlobalProperties.getProps();
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

    private void registerMessageConfigs() throws HandlerRegistrationException {
        registerMessageSerializers();
        registerMessageHandlers();
    }

    private void registerMessageSerializers() {
        registerMessageSerializer(channelId, EagerValMessage.ID, EagerValMessage.serializer);
    }

    @Override
    public Set<UUID> getMsgIds() {
        return messageBuffer.keySet();
    }

    @Override
    public Set<ProtoMessage> getMsgs() {
        return Set.copyOf(messageBuffer.values());
    }

    private void registerMessageHandlers() throws HandlerRegistrationException {
        registerMessageHandler(channelId, EagerValMessage.ID, (EagerValMessage msg1, Host from, short sourceProto, int channelId1) -> uponEagerValMessage(msg1), (msg, to, destProto, throwable, channelId) -> uponMsgFail(msg, to, throwable));
    }

    @Override
    public void init(Properties properties) {}

    @Override
    public void deliverMessage(ProtoMessage msg) {
        if (msg instanceof EagerValMessage) {
            deliverEagerValMessage((EagerValMessage) msg);
        } else {
            logger.error("Attempted to deliver state recovery message," +
                            " but it was not of type: {}",
                    EagerValMessage.class.getSimpleName());
        }
    }

    private void deliverEagerValMessage(EagerValMessage msg) {
        UUID mid = msg.getMid();
        messageBuffer.put(mid, msg);
        triggerNotification(new DeliverVal(msg.getVal()));
    }

    private void uponBroadcastRequest(EagerBroadcastRequest req, short source) {
        EagerValMessage msg = new EagerValMessage(UUID.randomUUID(), req.getVal());
        uponEagerValMessage(msg);
    }

    private void uponEagerValMessage(EagerValMessage msg) {
        if (!messageBuffer.containsKey(msg.getMid())) {
            messageBuffer.put(msg.getMid(), msg);
            if (msg.isBlocking()) {
                logger.debug("Received message {} is blocking. Waiting validation.", msg.getMid());
                recordValueToBeValidated(msg);
            } else {
                disseminateMessage(msg);
            }
            triggerNotification(new DeliverVal(msg.getVal()));
        } else {
            logger.debug("Received repeated message with id {}", msg.getMid());
        }
    }

    private void recordValueToBeValidated(EagerValMessage msg) {
        try {
            UUID blockingId = msg.getBlockingID();
            mapBlockingIdToMid.put(blockingId, msg);
        } catch (InnerValueIsNotBlockingBroadcast innerValueIsNotBlockingBroadcast) {
            innerValueIsNotBlockingBroadcast.printStackTrace();
        }
    }

    private void uponAnswerMessageValidationNotification(AnswerMessageValidationNotification notif) {
        EagerValMessage msg = mapBlockingIdToMid.get(notif.getBlockingMessageID());
        if (msg != null) {
            logger.debug("Continuing the dissemination of message {}.", msg.getMid());
            disseminateMessage(msg);
        }
    }

    @Override
    public Set<Host> getPeers() {
        return peers;
    }

    private void uponMsgFail(ProtoMessage msg, Host to,
                             Throwable throwable) {
        logger.error("Message {} to {} failed, reason: {}\n" +
                "Notifying peer sampling that this node is unreliable.", msg, to, throwable);
        triggerNotification(new PeerUnreachableNotification());
    }

    @Override
    public void sendMessageToPeer(ProtoMessage msg, Host target) {
        sendMessage(channelId,msg, target);
    }

    private void disseminateMessage(EagerValMessage msg) {
        peers.forEach(h -> sendMessage(channelId,msg, h));
        logger.debug("Sent message with value {} to peers {}", msg.getVal(), peers);
    }
}
