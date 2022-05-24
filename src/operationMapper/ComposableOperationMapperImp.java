package operationMapper;

import cmux.AppOperation;
import cmux.CMuxMask;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ComposableOperationMapperImp implements ComposableOperationMapper {

    private final OperationMapper inner;

    private final ReadWriteLock innerLock = new ReentrantReadWriteLock();

    public ComposableOperationMapperImp() {
        this.inner = new BaseOperationMapper();
    }

    public ComposableOperationMapperImp(OperationMapper inner) {
        this.inner = inner;
    }

    @Override
    public List<AppOperation> generateContentList(Collection<UUID> states, int usedSpace)
            throws IOException {
        try {
            innerLock.readLock().lock();
            return inner.generateContentList(states, usedSpace);
        } finally {
            innerLock.readLock().unlock();
        }
    }

    @Override
    public void submitContent(Collection<AppOperation> content) {
        try {
            innerLock.writeLock().lock();
            inner.submitContent(content);
        } finally {
            innerLock.writeLock().unlock();
        }
    }

    @Override
    public void submitContent(AppOperation content) {
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
    public Collection<AppOperation> getStoredContent() {
        try {
            innerLock.readLock().lock();
            return inner.getStoredContent();
        } finally {
            innerLock.readLock().unlock();
        }
    }

    @Override
    public Pair<ComposableOperationMapper, ComposableOperationMapper>
    separateContent(CMuxMask mask, OperationMapper innerLft, OperationMapper innerRgt) {
        ComposableOperationMapper lft = new ComposableOperationMapperImp(innerLft);
        ComposableOperationMapper rgt = new ComposableOperationMapperImp(innerRgt);
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
    private Set<UUID> redistributeContent(CMuxMask mask, ComposableOperationMapper lft, ComposableOperationMapper rgt) {
        Collection<AppOperation> allValues = inner.getStoredContent();
        Set<UUID> migrated = new HashSet<>((int) (0.6 * allValues.size()));
        for (AppOperation val : inner.getStoredContent()) {
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
    public void aggregateContent(Collection<ComposableOperationMapper> composableBlockConstructors) {
        try {
            innerLock.writeLock().lock();
            for (var blockConstructor : composableBlockConstructors)
                inner.submitContent(blockConstructor.getStoredContent());
        } finally {
            innerLock.writeLock().unlock();
        }
    }
}
