package sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers;

import java.util.Properties;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentBlockmessGPoETDifficultyComputer implements BlockmessGPoETDifficultyComputer {

    private final BlockmessGPoETDifficultyComputer inner;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ConcurrentBlockmessGPoETDifficultyComputer(Properties props, int numChains) {
        this.inner = new BlockmessGPoETDifficultyComputerImp(props, numChains);
    }

    @Override
    public int getSolutionLeadingZeros(byte[] solution) {
        return inner.getSolutionLeadingZeros(solution);
    }

    @Override
    public int getNumLeadingZeros() {
        try {
            lock.readLock().lock();
            return inner.getNumLeadingZeros();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean hasEnoughLeadingZeros(byte[] solution) {
        try {
            lock.readLock().lock();
            return inner.hasEnoughLeadingZeros(solution);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void setNumChains(int numChains) {
        try {
            lock.writeLock().lock();
            inner.setNumChains(numChains);
        } finally {
            lock.writeLock().unlock();
        }
    }

}
