package applicationInterface;

import ledger.AppContent;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import utils.IDGenerator;

import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ApplicationInterface extends GenericProtocol {

    private Set<UUID> enqueued = ConcurrentHashMap.newKeySet();

    public ApplicationInterface() {
        super(ApplicationInterface.class.getSimpleName(), IDGenerator.genId());
    }

    @Override
    public void init(Properties props) {}


    protected byte[] invokeOperation(byte[] operation) {
        OperationToCMuxIdentifierMapper mapper = OperationToCMuxIdentifierMapper.getSingleton();
        byte[] cmuxId1 = mapper.mapToCmuxId1(operation);
        byte[] cmuxId2 = mapper.mapToCmuxId2(operation);
        AppContent content = new AppContent(operation, cmuxId1, cmuxId2);
        return new byte[0];
    }

    public abstract byte[] processOperation(byte[] operation);

}
