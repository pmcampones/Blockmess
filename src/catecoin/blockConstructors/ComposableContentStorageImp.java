package catecoin.blockConstructors;

import catecoin.txs.Transaction;
import ledger.ledgerManager.StructuredValue;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ComposableContentStorageImp implements ComposableContentStorage<Transaction> {

    private final ContentStorage<StructuredValue<Transaction>> inner;

    private final ReadWriteLock innerLock = new ReentrantReadWriteLock();

    public ComposableContentStorageImp() throws PrototypeHasNotBeenDefinedException {
        this.inner = new BaseContentStorage();
    }

    public ComposableContentStorageImp(ContentStorage<StructuredValue<Transaction>> inner) {
        this.inner = inner;
    }

    @Override
    public List<StructuredValue<Transaction>> generateContentListList(Collection<UUID> states, int usedSpace)
            throws IOException {
        try {
            innerLock.readLock().lock();
            return inner.generateContentListList(states, usedSpace);
        } finally {
            innerLock.readLock().unlock();
        }
    }

    @Override
    public List<StructuredValue<Transaction>> generateBoundContentListList(Collection<UUID> states, int usedSpace, int maxTxs)
            throws IOException {
        try {
            innerLock.readLock().lock();
            return inner.generateBoundContentListList(states, usedSpace, maxTxs);
        } finally {
          innerLock.readLock().unlock();
        }
    }

    @Override
    public void submitContent(Collection<StructuredValue<Transaction>> content) {
        try {
            innerLock.writeLock().lock();
            inner.submitContent(content);
        } finally {
            innerLock.writeLock().unlock();
        }
    }

    @Override
    public void submitContent(StructuredValue<Transaction> content) {
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
    public Collection<StructuredValue<Transaction>> getStoredContent() {
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
    public Pair<ComposableContentStorage<Transaction>, ComposableContentStorage<Transaction>>
    separateContent(StructuredValueMask mask,
                    ContentStorage<StructuredValue<Transaction>> innerLft,
                    ContentStorage<StructuredValue<Transaction>> innerRgt) {
        ComposableContentStorage<Transaction> lft =
                new ComposableContentStorageImp(innerLft);
        ComposableContentStorage<Transaction> rgt =
                new ComposableContentStorageImp(innerRgt);
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
    private Set<UUID> redistributeContent(StructuredValueMask mask, ComposableContentStorage<Transaction> lft, ComposableContentStorage<Transaction> rgt) {
        Collection<StructuredValue<Transaction>> allValues = inner.getStoredContent();
        Set<UUID> migrated = new HashSet<>((int) (0.6 * allValues.size()));
        for (StructuredValue<Transaction> val : inner.getStoredContent()) {
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
    public void aggregateContent(Collection<ComposableContentStorage<Transaction>> composableBlockConstructors) {
        try {
            innerLock.writeLock().lock();
            for (var blockConstructor : composableBlockConstructors)
                inner.submitContent(blockConstructor.getStoredContent());
        } finally {
            innerLock.writeLock().unlock();
        }
    }
}
