package catecoin.blockConstructors;

import catecoin.mempoolManager.MempoolManager;
import catecoin.txs.IndexableContent;
import catecoin.txs.Transaction;
import ledger.ledgerManager.StructuredValue;
import main.GlobalProperties;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.toMap;

public class BaseContentStorage implements ContentStorage {

    public static final int MAX_BLOCK_SIZE = 40000;

    private final MempoolManager mempoolManager = MempoolManager.getSingleton();

    private final int maxBlockSize;
    private final int maxSizeOffset;
    private final Map<UUID, StructuredValue<Transaction>> contentMap = Collections.synchronizedMap(new TreeMap<>());

    public BaseContentStorage() {
        Properties props = GlobalProperties.getProps();
        this.maxBlockSize = parseInt(props.getProperty("maxBlockSize",String.valueOf(MAX_BLOCK_SIZE)));
        this.maxSizeOffset = 1000;
    }

    @Override
    public List<StructuredValue<Transaction>> generateContentListList(Collection<UUID> states, int usedSpace) throws IOException {
        Set<UUID> used = findUsedTransactions(states);
        return getContentDeterministicOrderBound(usedSpace, used);
    }

    private Set<UUID> findUsedTransactions(Collection<UUID> states) {
        Set<UUID> used = new HashSet<>();
        Set<UUID> visited = new HashSet<>();
        for (UUID state : states)
            used.addAll(mempoolManager.getInvalidTxsFromChunk(state, visited));
        return used;
    }

    @Override
    public void submitContent(Collection<StructuredValue<Transaction>> content) {
        contentMap.putAll(content.stream().collect(toMap(IndexableContent::getId,c->c)));
    }

    @Override
    public void submitContent(StructuredValue<Transaction> content) {
        contentMap.put(content.getId(), content);
    }

    @Override
    public Collection<StructuredValue<Transaction>> getStoredContent() {
        return contentMap.values();
    }

    @NotNull
    private List<StructuredValue<Transaction>> getContentDeterministicOrderBound(int usedSpace, Set<UUID> used) throws IOException {
        Iterator<Map.Entry<UUID, StructuredValue<Transaction>>> contentEntries = contentMap.entrySet().iterator();
        List<StructuredValue<Transaction>> content = new ArrayList<>();
        while (contentEntries.hasNext() && usedSpace < maxBlockSize - maxSizeOffset) {
            Map.Entry<UUID, StructuredValue<Transaction>> contentEntry = contentEntries.next();
            if (!used.contains(contentEntry.getKey())) {
                content.add(contentEntry.getValue());
                usedSpace += contentEntry.getValue().getSerializedSize();
            }
        }
        return content;
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        contentIds.forEach(contentMap::remove);
    }

}
