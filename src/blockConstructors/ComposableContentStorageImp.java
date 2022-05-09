package blockConstructors;

import ledger.AppContent;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ComposableContentStorageImp implements ComposableContentStorage {

    private final ContentStorage inner;

    private final ReadWriteLock innerLock = new ReentrantReadWriteLock();

    public ComposableContentStorageImp() {
        this.inner = new BaseContentStorage();
    }

    public ComposableContentStorageImp(ContentStorage inner) {
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
    public Pair<ComposableContentStorage, ComposableContentStorage>
    separateContent(CMuxMask mask, ContentStorage innerLft, ContentStorage innerRgt) {
        ComposableContentStorage lft = new ComposableContentStorageImp(innerLft);
        ComposableContentStorage rgt = new ComposableContentStorageImp(innerRgt);
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
    private Set<UUID> redistributeContent(CMuxMask mask, ComposableContentStorage lft, ComposableContentStorage rgt) {
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
    public void aggregateContent(Collection<ComposableContentStorage> composableBlockConstructors) {
        try {
            innerLock.writeLock().lock();
            for (var blockConstructor : composableBlockConstructors)
                inner.submitContent(blockConstructor.getStoredContent());
        } finally {
            innerLock.writeLock().unlock();
        }
    }
}
