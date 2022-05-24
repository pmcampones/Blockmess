package contentMapper;

import ledger.AppContent;
import main.GlobalProperties;
import mempoolManager.MempoolManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.toMap;

public class BaseContentMapper implements ContentMapper {

    public static final int MAX_BLOCK_SIZE = 40000;

    private final int maxBlockSize;
    private final int maxSizeOffset;
    private final Map<UUID, AppContent> contentMap = Collections.synchronizedMap(new TreeMap<>());

    public BaseContentMapper() {
        Properties props = GlobalProperties.getProps();
        this.maxBlockSize = parseInt(props.getProperty("maxBlockSize",String.valueOf(MAX_BLOCK_SIZE)));
        this.maxSizeOffset = 1000;
    }

    @Override
    public List<AppContent> generateContentList(Collection<UUID> states, int usedSpace) {
        Set<UUID> used = findUsedContent(states);
        return getContentDeterministicOrderBound(usedSpace, used);
    }

    private Set<UUID> findUsedContent(Collection<UUID> states) {
        Set<UUID> used = new HashSet<>();
        Set<UUID> visited = new HashSet<>();
        for (UUID state : states)
            used.addAll(MempoolManager.getSingleton().getUsedContentFromChunk(state, visited));
        return used;
    }

    @Override
    public void submitContent(Collection<AppContent> content) {
        contentMap.putAll(content.stream().collect(toMap(AppContent::getId, c->c)));
    }

    @Override
    public void submitContent(AppContent content) {
        contentMap.put(content.getId(), content);
    }

    @Override
    public Collection<AppContent> getStoredContent() {
        return contentMap.values();
    }

    @NotNull
    private List<AppContent> getContentDeterministicOrderBound(int usedSpace, Set<UUID> used) {
        Iterator<Map.Entry<UUID, AppContent>> contentEntries = contentMap.entrySet().iterator();
        List<AppContent> content = new ArrayList<>();
        while (contentEntries.hasNext() && usedSpace < maxBlockSize - maxSizeOffset) {
            Map.Entry<UUID, AppContent> contentEntry = contentEntries.next();
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
