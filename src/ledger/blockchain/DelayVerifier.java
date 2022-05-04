package ledger.blockchain;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class DelayVerifier<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>,? extends SybilElectionProof>> extends Thread implements AutoCloseable {

    //Tasks are simple, so the pool should be small.
    private static final int POOL_SIZE = 1;

    private static final ScheduledThreadPoolExecutor pool = initPool();

    private static ScheduledThreadPoolExecutor initPool() {
        ScheduledThreadPoolExecutor pool = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(POOL_SIZE);
        pool.setRemoveOnCancelPolicy(true);
        return pool;
    }

    private final long waitDelayReorder;

    private final Blockchain<B> blockchain;

    //Blocks received out of order are kept in this queue until they can be processed appropriately.
    private List<ArrivalTimeBlocks<B>> unorderedBlocks = new LinkedList<>();

    private ReentrantLock lock = new ReentrantLock();

    private final ScheduledFuture<?> task;

    public DelayVerifier(long waitDelayReorder, long verificationInterval, Blockchain<B> blockchain) {
        this.waitDelayReorder = waitDelayReorder;
        this.blockchain = blockchain;
        task = pool.scheduleAtFixedRate(this, verificationInterval, verificationInterval, TimeUnit.MILLISECONDS);
    }

    void submitUnordered(B block) {
        ArrivalTimeBlocks<B> arrivalBlock = new ArrivalTimeBlocks<>(block);
        try {
            lock.lock();
            unorderedBlocks.add(arrivalBlock);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void run() {
        pruneUnordered();
    }

    public void pruneUnordered() {
        try  {
            lock.lock();
            unorderedBlocks.removeIf(p -> System.currentTimeMillis() - p.arrival < waitDelayReorder);
        } finally {
            lock.unlock();
        }
    }

    public List<B> getOrderedBlocks() {
        try {
            lock.lock();
            return tryToGetOrderedBlocks();
        } finally {
            lock.unlock();
        }
    }

    @NotNull
    private List<B> tryToGetOrderedBlocks() {
        List<B> ordered = unorderedBlocks.stream()
                .map(pair -> pair.block)
                .filter(this::isOrdered)
                .collect(Collectors.toList());
        unorderedBlocks = unorderedBlocks.stream()
                .filter(p -> !ordered.contains(p.block))
                .collect(Collectors.toList());
        return ordered;
    }

    private boolean isOrdered(B block) {
        return block.getPrevRefs()
                .stream().allMatch(blockchain::hasBlock);
    }

    /*
            Used to record when blocks arrived in to the Ledger.
            It's possible that a block B_(i+1) arrives before a block B_i.
            B_(i+1) cannot be processed before B_i arrives, and so it is kept waiting.
            If B_i takes too long to arrive, B_(i+1) is discarded.
    */
    private static class ArrivalTimeBlocks<B extends LedgerBlock<?,?>> {

        //Arrival time for the block.
        private final long arrival;

        private final B block;

        private ArrivalTimeBlocks(B block) {
            this.block = block;
            arrival = System.currentTimeMillis();
        }
    }

    public void close() {
        task.cancel(false);
    }

}
