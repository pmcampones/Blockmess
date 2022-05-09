package valueDispatcher;

import broadcastProtocols.BroadcastValue;
import broadcastProtocols.eagerPush.EagerBroadcastRequest;
import broadcastProtocols.eagerPush.EagerPushBroadcast;
import broadcastProtocols.lazyPush.LazyPushBroadcast;
import broadcastProtocols.lazyPush.requests.LazyBroadcastRequest;
import broadcastProtocols.notifications.DeliverVal;
import catecoin.notifications.DeliverIndexableContentNotification;
import catecoin.txs.Transaction;
import ledger.blocks.LedgerBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import utils.IDGenerator;
import valueDispatcher.notifications.DeliverSignedBlockNotification;
import valueDispatcher.requests.DisseminateSignedBlockRequest;
import valueDispatcher.requests.DisseminateTransactionRequest;

import java.io.IOException;
import java.util.Properties;

/**
 * Middleware that separates the DL engine and Application protocols from the Broadcast and Peer Sampling protocols.
 * <p>Receives requests from the DL engine and Application to disseminate values and decides the Broadcast protocol to be used.</p>
 * <p>All content pertaining to the DL engine and Application from the Broadcast Protocols is delivered here.</p>
 * <p>The values disseminated are processed in 3 stages, if another is added, the following should be done.</p>
 *  <p>1st - Introduce a new type of message in the ValType enum. This enable the Dispatcher to identify values delivered from the Broadcasts.</p>
 *  <p>2nd - Register a request handler for the message type. It must contain a BroadcastValue instance,
 *      as these instances contain the necessary logic to be serialized by the Broadcast protocols irrespective of their contents.</p>
 *  <p>3rd - Add a new entry to the switch to forward the inner BroadcastValue to the pertinent protocols.</p>
 *      Doing a verification of the BroadcastValue type here is important but not necessary.
 *      Verifying here ensures malformed content is discarded without upsetting the logic of the more complex upper protocols.
 */
public class ValueDispatcher extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(ValueDispatcher.class);

    public static final short ID = IDGenerator.genId();

    private enum ValType {
        TRANSACTION,                //A transaction is being broadcast
        SIGNED_BLOCK,               //A Ledger Block is being broadcast
    }

    public static ValueDispatcher singleton;

    private ValueDispatcher() {
        super(ValueDispatcher.class.getSimpleName(), ID);
        try {
            tryToSetupDispatcher();
        } catch (HandlerRegistrationException e) {
            throw new RuntimeException(e);
        }
    }

    private void tryToSetupDispatcher() throws HandlerRegistrationException {
        subscribeNotification(DeliverVal.ID, this::uponDeliverVal);
        registerRequestHandlers();
        BroadcastValue.pojoSerializers.put(DispatcherWrapper.ID, DispatcherWrapper.serializer);
    }

    private void registerRequestHandlers() throws HandlerRegistrationException {
        registerRequestHandler(DisseminateSignedBlockRequest.ID, this::uponDisseminateBlockRequest);
        registerRequestHandler(DisseminateTransactionRequest.ID, this::uponDisseminateTransactionRequest);
    }

    public static ValueDispatcher getSingleton() {
        if (singleton == null)
            singleton = new ValueDispatcher();
        return singleton;
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
    private void notifyUpperProtocols(ValType type, BroadcastValue val) {
        switch (type) {
            case TRANSACTION:
                if (val instanceof Transaction)
                    triggerNotification(new DeliverIndexableContentNotification((Transaction) val));
                break;
            case SIGNED_BLOCK:
                if (val instanceof LedgerBlock)
                    triggerNotification(new DeliverSignedBlockNotification<>((LedgerBlock) val));
                break;
            default:
                logger.debug("Received unknown value type");
        }
    }

    private void uponDisseminateBlockRequest(DisseminateSignedBlockRequest req, short source) {
        try {
            logger.info("Protocol {} requested the dissemination of a {}",
                    source, ValType.SIGNED_BLOCK);
            sendLazyRequest(req.getBlock());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uponDisseminateTransactionRequest(DisseminateTransactionRequest req, short source) {
        try {
            logger.info("Protocol {} requested the dissemination of a {}",
                    source, ValType.TRANSACTION);
            sendEagerRequest(req.getTransaction());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendLazyRequest(BroadcastValue val) throws IOException {
        DispatcherWrapper wrapper = new DispatcherWrapper((short) ValType.SIGNED_BLOCK.ordinal(), val);
        sendRequest(new LazyBroadcastRequest(wrapper), LazyPushBroadcast.ID);
    }

    private void sendEagerRequest(BroadcastValue val) throws IOException {
        DispatcherWrapper wrapper = new DispatcherWrapper((short) ValType.TRANSACTION.ordinal(), val);
        sendRequest(new EagerBroadcastRequest(wrapper), EagerPushBroadcast.ID);
    }

}
