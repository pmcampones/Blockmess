package utils.merkleTree;

import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentMerkleTree implements MerkleTree {

    private MerkleTree inner;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ConcurrentMerkleTree(MerkleTree inner) {
        this.inner = inner;
    }

    @Override
    public byte[] getHashValue() {
        try {
            lock.readLock().lock();
            return inner.getHashValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void addLeaf(byte[] hashVal) {
        try {
            lock.writeLock().lock();
            inner.addLeaf(hashVal);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean removeLeaf(byte[] hashVal) {
        try {
            lock.writeLock().lock();
            return inner.removeLeaf(hashVal);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean replaceLeaf(byte[] oldVal, byte[] newVal) {
        try {
            lock.writeLock().lock();
            return inner.replaceLeaf(oldVal, newVal);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Set<byte[]> getLeaves() {
        try {
            lock.readLock().lock();
            return inner.getLeaves();
        } finally {
            lock.readLock().unlock();
        }
    }

}
