package contentMapper;

import cmux.AppContent;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ContentMapper {

    List<AppContent> generateContentList(Collection<UUID> states, int usedSpace) throws IOException;

    void submitContent(Collection<AppContent> content);

    void submitContent(AppContent content);

    void deleteContent(Set<UUID> contentIds);

    Collection<AppContent> getStoredContent();

}
