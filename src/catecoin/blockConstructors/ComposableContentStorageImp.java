package catecoin.blockConstructors;

import catecoin.txs.IndexableContent;
import ledger.ledgerManager.StructuredValue;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ComposableContentStorageImp<E extends IndexableContent>
        implements ComposableContentStorage<E> {

    private final ContentStorage<StructuredValue<E>> inner;

    private final ReadWriteLock innerLock = new ReentrantReadWriteLock();

    public ComposableContentStorageImp() throws PrototypeHasNotBeenDefinedException {
        this.inner = ContentStoragePrototype.getPrototype();
    }

    public ComposableContentStorageImp(ContentStorage<StructuredValue<E>> inner) {
        this.inner = inner;
    }

    @Override
    public List<StructuredValue<E>> generateBlockContentList(Collection<UUID> states, int usedSpace)
            throws IOException {
        try {
            innerLock.readLock().lock();
            return inner.generateBlockContentList(states, usedSpace);
        } finally {
            innerLock.readLock().unlock();
        }
    }

    @Override
    public List<StructuredValue<E>> generateBoundBlockContentList(Collection<UUID> states, int usedSpace, int maxTxs)
            throws IOException {
        try {
            innerLock.readLock().lock();
            return inner.generateBoundBlockContentList(states, usedSpace, maxTxs);
        } finally {
          innerLock.readLock().unlock();
        }
    }

    @Override
    public void submitContent(Collection<StructuredValue<E>> content) {
        try {
            innerLock.writeLock().lock();
            inner.submitContent(content);
        } finally {
            innerLock.writeLock().unlock();
        }
    }

    @Override
    public void submitContent(StructuredValue<E> content) {
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
    public Collection<StructuredValue<E>> getStoredContent() {
        try {
            innerLock.readLock().lock();
            return inner.getStoredContent();
        } finally {
            innerLock.readLock().unlock();
        }
    }

    @Override
    public void halveChainThroughput() {
        inner.halveChainThroughput();
    }

    @Override
    public void doubleChainThroughput() {
        inner.doubleChainThroughput();
    }

    @Override
    public int getThroughputReduction() {
        return inner.getThroughputReduction();
    }

    @Override
    public void setChainThroughputReduction(int reduction) {
        inner.setChainThroughputReduction(reduction);
    }

    @Override
    public Pair<ComposableContentStorage<E>, ComposableContentStorage<E>>
    separateContent(StructuredValueMask mask,
                    ContentStorage<StructuredValue<E>> innerLft,
                    ContentStorage<StructuredValue<E>> innerRgt) {
        ComposableContentStorage<E> lft =
                new ComposableContentStorageImp<>(innerLft);
        ComposableContentStorage<E> rgt =
                new ComposableContentStorageImp<>(innerRgt);
        try {
            innerLock.writeLock().lock();
            Set<UUID> migrated = redistributeContent(mask, lft, rgt);
            inner.deleteContent(migrated);
        } finally {
            innerLock.writeLock().unlock();
        }
        lft.setChainThroughputReduction(getThroughputReduction() * 2);
        rgt.setChainThroughputReduction(getThroughputReduction() * 2);
        return Pair.of(lft, rgt);
    }

    @NotNull
    private Set<UUID> redistributeContent(StructuredValueMask mask, ComposableContentStorage<E> lft, ComposableContentStorage<E> rgt) {
        Collection<StructuredValue<E>> allValues = inner.getStoredContent();
        Set<UUID> migrated = new HashSet<>((int) (0.6 * allValues.size()));
        for (StructuredValue<E> val : inner.getStoredContent()) {
            StructuredValueMask.MaskResult res = mask.matchIds(val.getMatch1(), val.getMatch2());
            if (res.equals(StructuredValueMask.MaskResult.LEFT)) {
                migrated.add(val.getId());
                lft.submitContent(val);
            } else if (res.equals(StructuredValueMask.MaskResult.RIGHT)) {
                migrated.add(val.getId());
                rgt.submitContent(val);
            }
        }
        return migrated;
    }

    @Override
    public void aggregateContent(
            Collection<ComposableContentStorage<E>> composableBlockConstructors) {
        try {
            innerLock.writeLock().lock();
            for (var blockConstructor : composableBlockConstructors)
                inner.submitContent(blockConstructor.getStoredContent());
        } finally {
            innerLock.writeLock().unlock();
        }
    }
}
