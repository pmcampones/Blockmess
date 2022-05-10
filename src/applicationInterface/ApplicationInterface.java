package applicationInterface;

import ledger.AppContent;
import main.BlockmessLauncher;
import org.apache.commons.lang3.tuple.Pair;
import org.thavam.util.concurrent.blockingMap.BlockingHashMap;
import org.thavam.util.concurrent.blockingMap.BlockingMap;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import utils.IDGenerator;
import valueDispatcher.ValueDispatcher;

import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import static org.bouncycastle.pqc.math.linearalgebra.ByteUtils.concatenate;

public abstract class ApplicationInterface extends GenericProtocol {

    private final AtomicLong localOpIdx = new AtomicLong(0);
    private long globalOpIdx = 0;
    private byte[] replicaId;

    private final BlockingMap<UUID, Pair<byte[], Long>> completedOperations = new BlockingHashMap<>();

    private final BlockingQueue<AppContent> queuedOperations = new LinkedBlockingQueue<>();

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

    private void processOperations() {
        while(true) {
            try {
                AppContent currentOp = queuedOperations.take();
                byte[] operationResult = processOperation(currentOp.getContent());
                completedOperations.put(currentOp.getId(), Pair.of(operationResult, globalOpIdx++));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected Pair<byte[], Long> invokeOperation(byte[] operation) {
        try {
            return tryToInvokeOperation(operation);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] numToBytes(long num) {
        return ByteBuffer.allocate(Long.BYTES).putLong(num).array();
    }

    private Pair<byte[], Long> tryToInvokeOperation(byte[] operation) throws InterruptedException {
        FixedCMuxIdentifierMapper mapper = FixedCMuxIdentifierMapper.getSingleton();
        byte[] cmuxId1 = mapper.mapToCmuxId1(operation);
        byte[] cmuxId2 = mapper.mapToCmuxId2(operation);
        byte[] replicaMetadata = makeMetadata();
        AppContent content = new AppContent(operation, cmuxId1, cmuxId2, replicaMetadata);
        ValueDispatcher.getSingleton().disseminateAppContentRequest(content);
        return completedOperations.take(content.getId());
    }

    private byte[] makeMetadata() {
        byte[] opIdx = numToBytes(localOpIdx.getAndIncrement());
        return concatenate(replicaId, opIdx);
    }

    public abstract byte[] processOperation(byte[] operation);

}
