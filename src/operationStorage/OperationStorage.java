package operationStorage;

import java.util.Collection;
import java.util.UUID;

public interface OperationStorage {

    void storeOperation(UUID opId, byte[] operation);

    void getOperations(Collection<UUID> opIds);

}
