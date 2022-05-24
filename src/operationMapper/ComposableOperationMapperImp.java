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
    public List<AppOperation> generateOperationList(Collection<UUID> states, int usedSpace)
            throws IOException {
        try {
            innerLock.readLock().lock();
            return inner.generateOperationList(states, usedSpace);
        } finally {
            innerLock.readLock().unlock();
        }
    }

    @Override
    public void submitOperations(Collection<AppOperation> operations) {
        try {
            innerLock.writeLock().lock();
            inner.submitOperations(operations);
        } finally {
            innerLock.writeLock().unlock();
        }
    }

    @Override
    public void submitOperation(AppOperation operation) {
        try {
            innerLock.writeLock().lock();
            inner.submitOperation(operation);
        } finally {
            innerLock.writeLock().unlock();
        }
    }

    @Override
    public void deleteOperations(Set<UUID> operatationIds) {
        try {
            innerLock.writeLock().lock();
            inner.deleteOperations(operatationIds);
        } finally {
            innerLock.writeLock().unlock();
        }
    }

    @Override
    public Collection<AppOperation> getStoredOperations() {
        try {
            innerLock.readLock().lock();
            return inner.getStoredOperations();
        } finally {
            innerLock.readLock().unlock();
        }
    }

    @Override
    public Pair<ComposableOperationMapper, ComposableOperationMapper>
    separateOperations(CMuxMask mask, OperationMapper innerLft, OperationMapper innerRgt) {
        ComposableOperationMapper lft = new ComposableOperationMapperImp(innerLft);
        ComposableOperationMapper rgt = new ComposableOperationMapperImp(innerRgt);
        try {
            innerLock.writeLock().lock();
            Set<UUID> migrated = redistributeContent(mask, lft, rgt);
            inner.deleteOperations(migrated);
        } finally {
            innerLock.writeLock().unlock();
        }
        return Pair.of(lft, rgt);
    }

    @NotNull
    private Set<UUID> redistributeContent(CMuxMask mask, ComposableOperationMapper lft, ComposableOperationMapper rgt) {
        Collection<AppOperation> allValues = inner.getStoredOperations();
        Set<UUID> migrated = new HashSet<>((int) (0.6 * allValues.size()));
        for (AppOperation val : inner.getStoredOperations()) {
            CMuxMask.MaskResult res = mask.matchIds(val.getCmuxId1(), val.getCmuxId2());
            if (res.equals(CMuxMask.MaskResult.LEFT)) {
                migrated.add(val.getId());
                lft.submitOperation(val);
            } else if (res.equals(CMuxMask.MaskResult.RIGHT)) {
                migrated.add(val.getId());
                rgt.submitOperation(val);
            }
        }
        return migrated;
    }

    @Override
    public void aggregateOperations(Collection<ComposableOperationMapper> operationMappers) {
        try {
            innerLock.writeLock().lock();
            for (var blockConstructor : operationMappers)
                inner.submitOperations(blockConstructor.getStoredOperations());
        } finally {
            innerLock.writeLock().unlock();
        }
    }
}
