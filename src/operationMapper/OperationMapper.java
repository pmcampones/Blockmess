package operationMapper;

import cmux.AppOperation;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface OperationMapper {

    List<AppOperation> generateContentList(Collection<UUID> states, int usedSpace) throws IOException;

    void submitContent(Collection<AppOperation> content);

    void submitContent(AppOperation content);

    void deleteContent(Set<UUID> contentIds);

    Collection<AppOperation> getStoredContent();

}
