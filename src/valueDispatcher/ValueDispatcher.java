package valueDispatcher;

import broadcastProtocols.eagerPush.EagerBroadcastRequest;
import broadcastProtocols.eagerPush.EagerPushBroadcast;
import broadcastProtocols.lazyPush.LazyPushBroadcast;
import broadcastProtocols.lazyPush.requests.LazyBroadcastRequest;
import broadcastProtocols.notifications.DeliverVal;
import catecoin.nodeJoins.AutomatedNodeJoin;
import catecoin.nodeJoins.InteractiveNodeJoin;
import catecoin.notifications.DeliverIndexableContentNotification;
import catecoin.txs.IndexableContent;
import catecoin.txs.SlimTransaction;
import chatApp.ChatMessage;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import main.ProtoPojo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.SybilElectionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.InElectionSortitionProof;
import utils.IDGenerator;
import valueDispatcher.notifications.*;
import valueDispatcher.pojos.DispatcherWrapper;
import valueDispatcher.requests.*;

import java.io.IOException;
import java.util.Properties;

/**
 * Middleware that separates the DL engine and Application protocols from the Broadcast and Peer Sampling protocols.
 * <p>Receives requests from the DL engine and Application to disseminate values and decides the Broadcast protocol to be used.</p>
 * <p>All content pertaining to the DL engine and Application from the Broadcast Protocols is delivered here.</p>
 * <p>The values disseminated are processed in 3 stages, if another is added, the following should be done.</p>
 *  <p>1st - Introduce a new type of message in the ValType enum. This enable the Dispatcher to identify values delivered from the Broadcasts.</p>
 *  <p>2nd - Register a request handler for the message type. It must contain a ProtoPojo instance,
 *      as these instances contain the necessary logic to be serialized by the Broadcast protocols irrespective of their contents.</p>
 *  <p>3rd - Add a new entry to the switch to forward the inner ProtoPojo to the pertinent protocols.</p>
 *      Doing a verification of the ProtoPojo type here is important but not necessary.
 *      Verifying here ensures malformed content is discarded without upsetting the logic of the more complex upper protocols.
 */
public class ValueDispatcher<B extends LedgerBlock<C,P>, C extends BlockContent<? extends IndexableContent>, P extends SybilElectionProof> extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(ValueDispatcher.class);

    public static final short ID = IDGenerator.genId();

    private enum ValType {

    /*******************************APP*************************************/
        INTERACTIVE_NODE_JOIN,      //A node joining the system notifies its peers of its existence.
        AUTOMATED_NODE_JOIN,        //A node joins the system for the automated client.
        TRANSACTION,                //A transaction is being broadcast
        SIGNED_BLOCK,               //A Ledger Block is being broadcast

    /***********************INTERMEDIATE*CONSENSUS**************************/
        LOWEST_COIN,
        SORTITION_PROOF,

    /********************************TESTING********************************/
        CHAT_MESSAGE, //Message used in the testing of the Broadcast Protocols and the ValueDispatcher itself
    }

    public ValueDispatcher() throws HandlerRegistrationException {
        super(ValueDispatcher.class.getSimpleName(), ID);
        subscribeNotification(DeliverVal.ID, this::uponDeliverVal);
        registerRequestHandlers();
        ProtoPojo.pojoSerializers.put(DispatcherWrapper.ID, DispatcherWrapper.serializer);
    }

    private void registerRequestHandlers() throws HandlerRegistrationException {
        registerRequestHandler(DisseminateInteractiveNodeJoinRequest.ID,
                this::uponDisseminateInteractiveNodeJoinRequest);
        registerRequestHandler(DisseminateAutomatedNodeJoinRequest.ID,
                this::uponDisseminateAutomatedNodeJoinRequest);
        registerRequestHandler(DisseminateChatMessageRequest.ID,
                this::uponDisseminateChatMessageRequest);
        registerRequestHandler(DisseminateSignedBlockRequest.ID,
                this::uponDisseminateBlockRequest);
        registerRequestHandler(DisseminateTransactionRequest.ID,
                this::uponDisseminateTransactionRequest);
        registerRequestHandler(DisseminateSortitionProofRequest.ID,
                this::uponDisseminateSortitionProofRequest);
    }

    @Override
    public void init(Properties properties) {}

    private void uponDeliverVal(DeliverVal v, short id) {
        if (v.getVal() instanceof DispatcherWrapper) {
            DispatcherWrapper wrapper = (DispatcherWrapper) v.getVal();
            short typeIndex = wrapper.getDispatcherType();
            if (typeIndex < ValType.values().length) {
                ValType type = ValType.values()[typeIndex];
                logger.info("Received disseminated content from {} of type {}", id, type);
                notifyUpperProtocols(type, wrapper.getVal());
            }
        }
    }

    //If the val is not of the correct instance, its assumed the message is incorrect and thus ignored.
    private void notifyUpperProtocols(ValType type, ProtoPojo val) {
        switch (type) {
            case INTERACTIVE_NODE_JOIN:
                if (val instanceof InteractiveNodeJoin)
                    triggerNotification(new DeliverInteractiveNodeJoinNotification((InteractiveNodeJoin) val));
                break;
            case AUTOMATED_NODE_JOIN:
                if (val instanceof AutomatedNodeJoin)
                    triggerNotification(new DeliverAutomatedNodeJoinNotification());
                break;
            case TRANSACTION:
                if (val instanceof SlimTransaction)
                    triggerNotification(new DeliverIndexableContentNotification((SlimTransaction) val));
                break;
            case SIGNED_BLOCK:
                if (val instanceof LedgerBlock)
                    triggerNotification(new DeliverSignedBlockNotification<>((LedgerBlock<C, P>) val));
                break;
            case CHAT_MESSAGE:
                if (val instanceof ChatMessage)
                    triggerNotification(new DeliverChatMessageNotification((ChatMessage) val));
                break;
            case SORTITION_PROOF:
                if (val instanceof InElectionSortitionProof)
                    triggerNotification(new DeliverSortitionProofNotification((InElectionSortitionProof) val));
                break;
        }
    }

    private void uponDisseminateInteractiveNodeJoinRequest(DisseminateInteractiveNodeJoinRequest req, short source) {
        try {
            logger.info("Protocol {} requested the dissemination of a {}",
                    source, ValType.INTERACTIVE_NODE_JOIN);
            sendEagerRequest(ValType.INTERACTIVE_NODE_JOIN, req.getInteractiveNodeJoin());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uponDisseminateAutomatedNodeJoinRequest(DisseminateAutomatedNodeJoinRequest req, short source) {
        try {
            logger.info("Protocol {} requested the dissemination of a {}",
                    source, ValType.AUTOMATED_NODE_JOIN);
            sendEagerRequest(ValType.AUTOMATED_NODE_JOIN, req.getAutomatedNodeJoin());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uponDisseminateChatMessageRequest(DisseminateChatMessageRequest req, short source) {
        try {
            logger.info("Protocol {} requested the dissemination of a {}",
                    source, ValType.CHAT_MESSAGE);
            sendEagerRequest(ValType.CHAT_MESSAGE, req.getChatMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uponDisseminateBlockRequest(DisseminateSignedBlockRequest<B> req, short source) {
        try {
            logger.info("Protocol {} requested the dissemination of a {}",
                    source, ValType.SIGNED_BLOCK);
            sendLazyRequest(ValType.SIGNED_BLOCK, req.getBlock());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uponDisseminateTransactionRequest(DisseminateTransactionRequest req, short source) {
        try {
            logger.info("Protocol {} requested the dissemination of a {}",
                    source, ValType.TRANSACTION);
            sendEagerRequest(ValType.TRANSACTION, req.getTransaction());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uponDisseminateSortitionProofRequest(DisseminateSortitionProofRequest req, short source) {
        try {
            logger.info("Protocol {} requested the dissemination of a {}",
                    source, ValType.SORTITION_PROOF);
            sendEagerRequest(ValType.SORTITION_PROOF, req.getProof());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendLazyRequest(ValType type, ProtoPojo val) throws IOException {
        DispatcherWrapper wrapper = new DispatcherWrapper((short) type.ordinal(), val);
        sendRequest(new LazyBroadcastRequest(wrapper), LazyPushBroadcast.ID);
    }

    private void sendEagerRequest(ValType type, ProtoPojo val) throws IOException {
        DispatcherWrapper wrapper = new DispatcherWrapper((short) type.ordinal(), val);
        sendRequest(new EagerBroadcastRequest(wrapper), EagerPushBroadcast.ID);
    }

}
