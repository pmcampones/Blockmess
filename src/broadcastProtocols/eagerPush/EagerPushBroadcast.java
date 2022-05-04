package broadcastProtocols.eagerPush;

import broadcastProtocols.BlockingBroadcast;
import broadcastProtocols.BroadcastProtocol;
import broadcastProtocols.PeriodicPrunableHashMap;
import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import broadcastProtocols.notifications.DeliverVal;
import broadcastProtocols.notifications.PeerUnreachableNotification;
import catecoin.notifications.AnswerMessageValidationNotification;
import main.ProtoPojo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peerSamplingProtocols.hyparview.HyparView;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.IDGenerator;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EagerPushBroadcast extends GenericProtocol implements BroadcastProtocol {

    private static final Logger logger = LogManager.getLogger(EagerPushBroadcast.class);

    public static final short ID = IDGenerator.genId();

    //Milliseconds

    /**
     * Underlying peer sampling protocol.
     */
    private final HyparView membership;

    private final Map<UUID, EagerValMessage> messageBuffer;

    /**
     * Maps the identifiers of objects that block in the broadcast
     * (see {@link BlockingBroadcast})
     *  to the identifiers of the messages that encapsulated them.
     *  <p>This is used to resume the broadcast of an object after it has been successfully validated.</p>
     */
    private final Map<UUID, EagerValMessage> mapBlockingIdToMid = new ConcurrentHashMap<>();

    public EagerPushBroadcast(HyparView membership,
                              PeriodicPrunableHashMap<UUID, EagerValMessage> messageBuffer)
            throws HandlerRegistrationException {
        super(EagerPushBroadcast.class.getSimpleName(), ID);
        this.membership = membership;
        this.messageBuffer = messageBuffer;
        subscribeNotification(AnswerMessageValidationNotification.ID,
                (AnswerMessageValidationNotification notif, short source) -> uponAnswerMessageValidationNotification(notif));
        registerRequestHandler(EagerBroadcastRequest.ID, this::uponBroadcastRequest);
        registerMessageConfigs();
    }

    private void registerMessageConfigs() throws HandlerRegistrationException {
        int cId = membership.getChannelID();
        registerSharedChannel(cId);
        registerMessageSerializers(cId);
        registerMessageHandlers(cId);
    }

    private void registerMessageSerializers(int cId) {
        registerMessageSerializer(cId, EagerValMessage.ID, EagerValMessage.serializer);
    }

    private void registerMessageHandlers(int cId) throws HandlerRegistrationException {
        registerMessageHandler(cId, EagerValMessage.ID, (EagerValMessage msg1, Host from, short sourceProto, int channelId1) -> uponEagerValMessage(msg1), (msg, to, destProto, throwable, channelId) -> uponMsgFail(msg, to, throwable));
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {}

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
        return membership.getPeers();
    }

    @Override
    public void sendMessageToPeer(ProtoMessage msg, Host target) {
        sendMessage(msg, target);
    }

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
        ProtoPojo val = req.getVal();
        EagerValMessage msg = new EagerValMessage(UUID.randomUUID(), val);
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

    private void disseminateMessage(EagerValMessage msg) {
        Set<Host> peers = membership.getPeers();
        peers.forEach(h -> sendMessage(msg, h));
        logger.debug("Sent message with value {} to peers {}", msg.getVal(), peers);
    }

    private void uponMsgFail(ProtoMessage msg, Host to,
                             Throwable throwable) {
        logger.error("Message {} to {} failed, reason: {}\n" +
                "Notifying peer sampling that this node is unreliable.", msg, to, throwable);
        triggerNotification(new PeerUnreachableNotification());
    }
}
