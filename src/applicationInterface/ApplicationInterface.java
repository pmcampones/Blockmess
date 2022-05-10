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

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class ApplicationInterface extends GenericProtocol {

    private long opIdx = 0;

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
        new Thread(this::processOperations).start();
    }

    private void processOperations() {
        while(true) {
            try {
                AppContent currentOp = queuedOperations.take();
                byte[] operationResult = processOperation(currentOp.getContent());
                completedOperations.put(currentOp.getId(), Pair.of(operationResult, opIdx++));
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

    private Pair<byte[], Long> tryToInvokeOperation(byte[] operation) throws InterruptedException {
        FixedCMuxIdentifierMapper mapper = FixedCMuxIdentifierMapper.getSingleton();
        byte[] cmuxId1 = mapper.mapToCmuxId1(operation);
        byte[] cmuxId2 = mapper.mapToCmuxId2(operation);
        AppContent content = new AppContent(operation, cmuxId1, cmuxId2);
        ValueDispatcher.getSingleton().disseminateAppContentRequest(content);
        return completedOperations.take(content.getId());
    }

    public abstract byte[] processOperation(byte[] operation);

}
