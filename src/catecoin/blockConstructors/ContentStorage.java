package catecoin.blockConstructors;

import catecoin.txs.Transaction;
import ledger.ledgerManager.StructuredValue;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ContentStorage {

    List<StructuredValue<Transaction>> generateContentListList(Collection<UUID> states, int usedSpace) throws IOException;

    void submitContent(Collection<StructuredValue<Transaction>> content);

    void submitContent(StructuredValue<Transaction> content);

    void deleteContent(Set<UUID> contentIds);

    Collection<StructuredValue<Transaction>> getStoredContent();

}
