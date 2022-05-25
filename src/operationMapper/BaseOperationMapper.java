package operationMapper;

import cmux.AppOperation;
import main.GlobalProperties;
import mempoolManager.MempoolManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.toMap;

public class BaseOperationMapper implements OperationMapper {

    public static final int MAX_BLOCK_SIZE = 40000;

    private final int maxBlockSize;
    private final int maxSizeOffset;
    private final Map<UUID, AppOperation> contentMap = Collections.synchronizedMap(new TreeMap<>());

    public BaseOperationMapper() {
        Properties props = GlobalProperties.getProps();
        this.maxBlockSize = parseInt(props.getProperty("maxBlockSize",String.valueOf(MAX_BLOCK_SIZE)));
        this.maxSizeOffset = 1000;
    }

    @Override
    public List<AppOperation> generateOperationList(Collection<UUID> states, int usedSpace) {
        Set<UUID> used = findUsedContent(states);
        return getContentDeterministicOrderBound(0, used);
    }

    private Set<UUID> findUsedContent(Collection<UUID> states) {
        Set<UUID> used = new HashSet<>();
        Set<UUID> visited = new HashSet<>();
        for (UUID state : states)
            used.addAll(MempoolManager.getSingleton().getUsedContentFromChunk(state, visited));
        return used;
    }

    @Override
    public void submitOperations(Collection<AppOperation> operations) {
        contentMap.putAll(operations.stream().collect(toMap(AppOperation::getId, c->c)));
    }

    @Override
    public void submitOperation(AppOperation operation) {
        contentMap.put(operation.getId(), operation);
    }

    @Override
    public void deleteOperations(Set<UUID> operatationIds) {
        operatationIds.forEach(contentMap::remove);
    }

    @NotNull
    private List<AppOperation> getContentDeterministicOrderBound(int usedSpace, Set<UUID> used) {
        Iterator<Map.Entry<UUID, AppOperation>> contentEntries = contentMap.entrySet().iterator();
        List<AppOperation> content = new ArrayList<>();
        while (contentEntries.hasNext() && usedSpace < maxBlockSize - maxSizeOffset) {
            Map.Entry<UUID, AppOperation> contentEntry = contentEntries.next();
            if (!used.contains(contentEntry.getKey())) {
                content.add(contentEntry.getValue());
                usedSpace += contentEntry.getValue().getSerializedSize();
            }
        }
        return content;
    }

    @Override
    public Collection<AppOperation> getStoredOperations() {
        return contentMap.values();
    }

}
