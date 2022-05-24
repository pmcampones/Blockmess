package contentMapper;

import cmux.AppContent;
import cmux.CMuxMask;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ComposableContentMapperImp implements ComposableContentMapper {

    private final ContentMapper inner;

    private final ReadWriteLock innerLock = new ReentrantReadWriteLock();

    public ComposableContentMapperImp() {
        this.inner = new BaseContentMapper();
    }

    public ComposableContentMapperImp(ContentMapper inner) {
        this.inner = inner;
    }

    @Override
    public List<AppContent> generateContentList(Collection<UUID> states, int usedSpace)
            throws IOException {
        try {
            innerLock.readLock().lock();
            return inner.generateContentList(states, usedSpace);
        } finally {
            innerLock.readLock().unlock();
        }
    }

    @Override
    public void submitContent(Collection<AppContent> content) {
        try {
            innerLock.writeLock().lock();
            inner.submitContent(content);
        } finally {
            innerLock.writeLock().unlock();
        }
    }

    @Override
    public void submitContent(AppContent content) {
        try {
            innerLock.writeLock().lock();
            inner.submitContent(content);
        } finally {
            innerLock.writeLock().unlock();
        }
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        try {
            innerLock.writeLock().lock();
            inner.deleteContent(contentIds);
        } finally {
            innerLock.writeLock().unlock();
        }
    }

    @Override
    public Collection<AppContent> getStoredContent() {
        try {
            innerLock.readLock().lock();
            return inner.getStoredContent();
        } finally {
            innerLock.readLock().unlock();
        }
    }

    @Override
    public Pair<ComposableContentMapper, ComposableContentMapper>
    separateContent(CMuxMask mask, ContentMapper innerLft, ContentMapper innerRgt) {
        ComposableContentMapper lft = new ComposableContentMapperImp(innerLft);
        ComposableContentMapper rgt = new ComposableContentMapperImp(innerRgt);
        try {
            innerLock.writeLock().lock();
            Set<UUID> migrated = redistributeContent(mask, lft, rgt);
            inner.deleteContent(migrated);
        } finally {
            innerLock.writeLock().unlock();
        }
        return Pair.of(lft, rgt);
    }

    @NotNull
    private Set<UUID> redistributeContent(CMuxMask mask, ComposableContentMapper lft, ComposableContentMapper rgt) {
        Collection<AppContent> allValues = inner.getStoredContent();
        Set<UUID> migrated = new HashSet<>((int) (0.6 * allValues.size()));
        for (AppContent val : inner.getStoredContent()) {
            CMuxMask.MaskResult res = mask.matchIds(val.getCmuxId1(), val.getCmuxId2());
            if (res.equals(CMuxMask.MaskResult.LEFT)) {
                migrated.add(val.getId());
                lft.submitContent(val);
            } else if (res.equals(CMuxMask.MaskResult.RIGHT)) {
                migrated.add(val.getId());
                rgt.submitContent(val);
            }
        }
        return migrated;
    }

    @Override
    public void aggregateContent(Collection<ComposableContentMapper> composableBlockConstructors) {
        try {
            innerLock.writeLock().lock();
            for (var blockConstructor : composableBlockConstructors)
                inner.submitContent(blockConstructor.getStoredContent());
        } finally {
            innerLock.writeLock().unlock();
        }
    }
}
