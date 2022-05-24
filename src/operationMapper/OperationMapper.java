package operationMapper;

import cmux.AppOperation;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface OperationMapper {

    List<AppOperation> generateOperationList(Collection<UUID> states, int usedSpace) throws IOException;

    void submitOperations(Collection<AppOperation> operations);

    void submitOperation(AppOperation operation);

    void deleteOperations(Set<UUID> operatationIds);

    Collection<AppOperation> getStoredOperations();

}
