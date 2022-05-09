package applicationInterface;

import ledger.AppContent;
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

    private final BlockingMap<UUID, byte[]> completedOperations = new BlockingHashMap<>();

    private final BlockingQueue<AppContent> queuedOperations = new LinkedBlockingQueue<>();

    public ApplicationInterface() {
        super(ApplicationInterface.class.getSimpleName(), IDGenerator.genId());
        try {
            subscribeNotification(DeliverFinalizedContentNotification.ID, this::uponDeliverFinalizedContentNotification);
        } catch (HandlerRegistrationException e) {
            throw new RuntimeException(e);
        }
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
                completedOperations.put(currentOp.getId(), operationResult);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected byte[] invokeOperation(byte[] operation) {
        FixedCMuxIdentifierMapper mapper = FixedCMuxIdentifierMapper.getSingleton();
        byte[] cmuxId1 = mapper.mapToCmuxId1(operation);
        byte[] cmuxId2 = mapper.mapToCmuxId2(operation);
        AppContent content = new AppContent(operation, cmuxId1, cmuxId2);
        ValueDispatcher.getSingleton().disseminateAppContentRequest(content);
        return completedOperations.get(content.getId());
    }

    public abstract byte[] processOperation(byte[] operation);

}
