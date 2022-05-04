package broadcastProtocols;

import broadcastProtocols.messages.BatcheableMessage;
import broadcastProtocols.messages.ReplyRecoveryContentMessage;
import broadcastProtocols.messages.RequestRecoveryContentMessage;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peerSamplingProtocols.notifications.RequireStateRecoveryNotification;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.babel.handlers.MessageFailedHandler;
import pt.unl.fct.di.novasys.babel.handlers.MessageInHandler;
import pt.unl.fct.di.novasys.babel.handlers.NotificationHandler;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

/**
 * Generic aspects of a Broadcast protocol.
 * <p>Contains the buffer to filter duplicates and registers the channel.
 * Thus classes extending this should not repeat these aspects.</p>
 * <p>Contains the code to perform state recovery of nodes that became isolated.</p>
 */
public class StateRecoveryBroadcastModule {

    private static final Logger logger = LogManager.getLogger(StateRecoveryBroadcastModule.class);

    private final BroadcastProtocol broadcastProtocol;

    public StateRecoveryBroadcastModule(BroadcastProtocol broadcastProtocol) {
        this.broadcastProtocol = broadcastProtocol;
    }

    public static List<Pair<Short, ISerializer<? extends ProtoMessage>>> getSerializers() {
        return List.of(
                Pair.of(RequestRecoveryContentMessage.ID, RequestRecoveryContentMessage.serializer),
                Pair.of(ReplyRecoveryContentMessage.ID, ReplyRecoveryContentMessage.serializer)
        );
    }

    public List<Triple<Short, MessageInHandler<ProtoMessage>, MessageFailedHandler<ProtoMessage>>> getMessageHandlers() {
        return List.of(
                Triple.of(RequestRecoveryContentMessage.ID,
                        (msg3, from1, source3, channel3) -> uponRequestRecoveryContentMessage(msg3, from1),
                        (msg, to, source, throwable, channel) -> uponFailureSendingRequestRecoveryContentMessage(to)),
                Triple.of(ReplyRecoveryContentMessage.ID,
                        (msg2, from, source2, channel2) -> uponReplyRecoveryContentMessage(msg2, from),
                        (msg1, to1, source1, throwable1, channel1) -> uponFailureSendingReplyRecoveryContentMessage(to1, throwable1))
        );
    }

    public List<Pair<Short, NotificationHandler<? extends ProtoNotification>>>  getNotificationSubscriptions() {
        return List.of(
            Pair.of(RequireStateRecoveryNotification.ID, (notif, source) -> uponRequireStateRecoveryNotification())
        );
    }

    private void uponRequestRecoveryContentMessage(ProtoMessage msg, Host from) {
        if (msg instanceof RequestRecoveryContentMessage) {
            answerRequestRecoveryContent((RequestRecoveryContentMessage) msg, from);
            logger.debug("Answering state recovery content to node: {}", from);
        } else {
            logger.error("Attempted to answer state recovery content to node: {}.\n" +
                    "However, the message received is not of the expected type.", from);
        }
    }

    private void answerRequestRecoveryContent(RequestRecoveryContentMessage msg, Host requester) {
        Set<BatcheableMessage> recoveryContent = broadcastProtocol.getMsgs()
                .stream()
                .filter(m -> m instanceof BatcheableMessage)
                .map(m -> (BatcheableMessage) m)
                .filter(m -> !msg.getIHaveIdentifiers().contains(m.getMid()))
                .collect(toSet());
        ReplyRecoveryContentMessage replyMsg = new ReplyRecoveryContentMessage(recoveryContent);
        broadcastProtocol.sendMessageToPeer(replyMsg, requester);
    }

    private void uponReplyRecoveryContentMessage(ProtoMessage msg, Host from) {
        if (msg instanceof ReplyRecoveryContentMessage) {
            ReplyRecoveryContentMessage replyMsg = (ReplyRecoveryContentMessage) msg;
            logger.info("Delivering {} received state recovery messages from {}.",
                    replyMsg.getRecoveryContent().size(), from);
            replyMsg.getRecoveryContent().forEach(broadcastProtocol::deliverMessage);
        } else {
            logger.error("Received state recovery reply content from {}.\n" +
                    "However, the message received is not of the expected type.", from);
        }
    }

    private void uponFailureSendingReplyRecoveryContentMessage(Host to,
                                                               Throwable throwable) {
        logger.error("Attempted to send state recovery content to {}, but failed because: {}",
                to, throwable.getMessage());
    }

    private void uponFailureSendingRequestRecoveryContentMessage (Host to) {
        sendRequestRecoveryMessage(Set.of(to));
    }

    private void uponRequireStateRecoveryNotification() {
        sendRequestRecoveryMessage(emptySet());
    }

    private void sendRequestRecoveryMessage(Set<Host> excluded) {
        Set<Host> peers = broadcastProtocol.getPeers();
        Optional<Host> to = peers.stream()
                .filter(n -> !excluded.contains(n)).findAny();
        if (to.isPresent()) {
            Set<UUID> iHave = broadcastProtocol.getMsgIds();
            RequestRecoveryContentMessage msg = new RequestRecoveryContentMessage(iHave);
            broadcastProtocol.sendMessageToPeer(msg, to.get());
            logger.debug("Requesting missing content to node: {}", to.get());
        } else {
            logger.error("Attempted to request missing content for node, but I have no peers to contact.");
        }
    }

}
