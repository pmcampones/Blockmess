package applicationInterface;

import ledger.AppContent;
import main.BlockmessLauncher;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.thavam.util.concurrent.blockingMap.BlockingHashMap;
import org.thavam.util.concurrent.blockingMap.BlockingMap;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import utils.IDGenerator;
import valueDispatcher.ValueDispatcher;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import static org.bouncycastle.pqc.math.linearalgebra.ByteUtils.concatenate;

public abstract class ApplicationInterface extends GenericProtocol {

    private final AtomicLong localOpIdx = new AtomicLong(0);
    private long globalOpIdx = 0;

    private final BlockingMap<UUID, Pair<byte[], Long>> completedSyncOperations = new BlockingHashMap<>();

    private final Map<UUID, ReplyListener> operationListeners = new ConcurrentHashMap<>();

    private final Set<UUID> operationsWaitingResponse = ConcurrentHashMap.newKeySet();

    private final BlockingQueue<AppContent> queuedOperations = new LinkedBlockingQueue<>();

    private byte[] replicaId;

    public ApplicationInterface(String[] blockmessProperties) {
        super(ApplicationInterface.class.getSimpleName(), IDGenerator.genId());
        try {
            subscribeNotification(DeliverFinalizedContentNotification.ID, this::uponDeliverFinalizedContentNotification);
        } catch (HandlerRegistrationException e) {
            throw new RuntimeException(e);
        }
        BlockmessLauncher.launchBlockmess(blockmessProperties, this);
    }

    private void uponDeliverFinalizedContentNotification(DeliverFinalizedContentNotification notif, short source) {
        queuedOperations.addAll(notif.getFinalizedContent());
    }

    @Override
    public void init(Properties props) {
        String address = props.getProperty("address");
        int port = Integer.parseInt(props.getProperty("port"));
        replicaId = concatenate(address.getBytes(), numToBytes(port));
        new Thread(this::processOperations).start();
    }

    private static byte[] numToBytes(long num) {
        return ByteBuffer.allocate(Long.BYTES).putLong(num).array();
    }

    private void processOperations() {
        while(true) {
            try {
                AppContent currentOp = queuedOperations.take();
                byte[] operationResult = processOperation(currentOp.getContent());
                if (operationsWaitingResponse.remove(currentOp.getId()))
                    replyOperationResults(currentOp, operationResult);
                globalOpIdx++;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void replyOperationResults(AppContent currentOp, byte[] operationResult) {
        ReplyListener listener = operationListeners.remove(currentOp.getId());
        Pair<byte[], Long> operationReply = Pair.of(operationResult, globalOpIdx);
        if (listener != null)
            listener.processReply(operationReply);
        else
            completedSyncOperations.put(currentOp.getId(), operationReply);
    }

    public Pair<byte[], Long> invokeSyncOperation(byte[] operation) {
        try {
            return tryToInvokeOperation(operation);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Pair<byte[], Long> tryToInvokeOperation(byte[] operation) throws InterruptedException {
        AppContent content = computeAppContent(operation);
        operationsWaitingResponse.add(content.getId());
        ValueDispatcher.getSingleton().disseminateAppContentRequest(content);
        return completedSyncOperations.take(content.getId());
    }

    @NotNull
    private AppContent computeAppContent(byte[] operation) {
        FixedCMuxIdentifierMapper mapper = FixedCMuxIdentifierMapper.getSingleton();
        byte[] cmuxId1 = mapper.mapToCmuxId1(operation);
        byte[] cmuxId2 = mapper.mapToCmuxId2(operation);
        byte[] replicaMetadata = makeMetadata();
        AppContent content = new AppContent(operation, cmuxId1, cmuxId2, replicaMetadata);
        return content;
    }

    public void invokeAsyncOperation(byte[] operation, ReplyListener listener) {
        AppContent content = computeAppContent(operation);
        operationsWaitingResponse.add(content.getId());
        operationListeners.put(content.getId(), listener);
        ValueDispatcher.getSingleton().disseminateAppContentRequest(content);
    }

    private byte[] makeMetadata() {
        byte[] opIdx = numToBytes(localOpIdx.getAndIncrement());
        return concatenate(replicaId, opIdx);
    }

    public abstract byte[] processOperation(byte[] operation);

}
