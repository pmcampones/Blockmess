package catecoin.blockConstructors;

import ledger.ledgerManager.StructuredValue;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ContentStorage {

    List<StructuredValue> generateContentListList(Collection<UUID> states, int usedSpace) throws IOException;

    void submitContent(Collection<StructuredValue> content);

    void submitContent(StructuredValue content);

    void deleteContent(Set<UUID> contentIds);

    Collection<StructuredValue> getStoredContent();

}
