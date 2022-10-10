package ledger.blockchain;

import ledger.Ledger;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import main.GlobalProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import validators.ApplicationObliviousValidator;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Long.parseLong;

public class Blockchain implements Ledger {

	public static final int FINALIZED_WEIGHT = 6;
	private static final Logger logger = LogManager.getLogger(Blockchain.class.getName());
	private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors() / 4;
	private static final long WAIT_DELAY_REORDER = 1000 * 5;
	private static final long VERIFICATION_INTERVAL = WAIT_DELAY_REORDER * 5;
	private static final ScheduledThreadPoolExecutor pool = initPool();


	//Blocks that have arrived but are yet to be processed.
	//This Blockchain implementation is fundamentally serial.
	//Blocks are kept in this queue and a single thread will process each individually in a FIFO order.
	private final BlockingQueue<BlockmessBlock> toProcess = new LinkedBlockingQueue<>();

	private final List<LedgerObserver> observers = new LinkedList<>();
	private final DelayVerifier delayVerifier;
	private final ScheduledFuture<?> task;

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private final BlockFinalizer blockFinalizer;


	public Blockchain() {
		this(computeGenesisId());
	}

	private static UUID computeGenesisId() {
		Properties props = GlobalProperties.getProps();
		String genesisUUIDStr = props.getProperty("genesisUUID",
				"00000000-0000-0000-0000-000000000000");
		return UUID.fromString(genesisUUIDStr);
	}

	public Blockchain(UUID genesisId) {
		Properties props = GlobalProperties.getProps();
		this.blockFinalizer = new BlockFinalizer(genesisId);
		this.delayVerifier = generateDelayVerifier(props);
		int expectedTimeBetweenBlocks = Integer.parseInt(props.getProperty("expectedTimeBetweenBlocks"));
		this.task = pool.scheduleAtFixedRate(new QueuePoller(), expectedTimeBetweenBlocks / 5, expectedTimeBetweenBlocks / 5, TimeUnit.MILLISECONDS);

	}

	private DelayVerifier generateDelayVerifier(Properties props) {
		long waitDelayReorder = parseLong(props.getProperty("waitDelayReorder",
				String.valueOf(WAIT_DELAY_REORDER)));
		long verificationDelay = parseLong(props.getProperty("verificationDelay",
				String.valueOf(VERIFICATION_INTERVAL)));
		return new DelayVerifier(waitDelayReorder, verificationDelay, this);
	}

	private static ScheduledThreadPoolExecutor initPool() {
		ScheduledThreadPoolExecutor pool = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(POOL_SIZE);
		pool.setRemoveOnCancelPolicy(true);
		return pool;
	}

	boolean hasBlock(UUID blockId) {
		return blockFinalizer.hasBlock(blockId);
	}

	private class QueuePoller extends Thread {

		public void run() {
			toProcess.addAll(delayVerifier.getOrderedBlocks());
			processBlocks();
		}

	}

	private void processBlock(BlockmessBlock block) {
		long start = System.currentTimeMillis();
		List<UUID> prev = block.getPrevRefs();
		if (prev.size() != 1) {
			logger.info("Received malformed block with id: {}", block.getBlockId());
		} else if (!blockFinalizer.hasBlock(prev.get(0))) {
			logger.info("Received unordered block with id: {}, referencing {}",
					block.getBlockId(), block.getPrevRefs().get(0));
			delayVerifier.submitUnordered(block);
		} else if (ApplicationObliviousValidator.getSingleton().isBlockValid(block)) {
			processValidBlock(block);
		} else {
			logger.info("Received invalid block {} referencing {}",
					block.getBlockId(), block.getPrevRefs().get(0));
		}
		long end = System.currentTimeMillis();
		logger.info("Elapsed time in processing block {}: {} miliseconds",
				block.getBlockId(), (end - start));
	}

	private void processValidBlock(BlockmessBlock block) {
		List<UUID> prev = block.getPrevRefs();
		logger.debug("Processing valid block {}", block.getBlockId());
		int weight = blockFinalizer.getWeight(prev.get(0)) + block.getInherentWeight();//blocks.get(prev.get(0)).getWeight() + block.getInherentWeight();
		var deliverFinalizedInfo = blockFinalizer.addBlock(block.getBlockId(),
				new HashSet<>(prev), block.getInherentWeight());
		deliverFinalizedBlocks(deliverFinalizedInfo.getLeft(), deliverFinalizedInfo.getRight());
		deliverNonFinalizedBlock(block, weight);
	}

	private void deliverFinalizedBlocks(List<UUID> finalizedIds, Set<UUID> deleted) {
		for (LedgerObserver observer : this.observers)
			observer.deliverFinalizedBlocks(finalizedIds, deleted);
	}

	@Override
	public Set<UUID> getBlockR() {
		try {
			lock.readLock().lock();
			return blockFinalizer.getBlockR();
		} finally {
			lock.readLock().unlock();
		}
	}


	@Override
	public void submitBlock(BlockmessBlock block) {
		logger.debug("Received block {}, referencing {}", block.getBlockId(), block.getPrevRefs());
		toProcess.add(block);
		processBlocks();
	}

	@Override
	public void attachObserver(LedgerObserver observer) {
		this.observers.add(observer);
	}


	public Set<UUID> getFollowing(UUID block, int distance) {
		return blockFinalizer.getFollowing(block, distance);
	}

	@Override
	public int getWeight(UUID block) throws IllegalArgumentException {
		return blockFinalizer.getWeight(block);
	}

	@Override
	public boolean isInLongestChain(UUID nodeId) {
		try {
			lock.readLock().lock();
			return blockFinalizer.isInLongestChain(nodeId);
		} finally {
			lock.readLock().unlock();
		}
	}

	public int getFinalizedWeight() {
		return blockFinalizer.getFinalizedWeight();
	}

	@Override
	public Set<UUID> getForkBlocks(int depth) throws IllegalArgumentException {
		if (depth < 0)
			throw new IllegalArgumentException();
		return blockFinalizer.getForkBlocks(getBlockR(), depth);
	}

	@Override
	public void close() {
		task.cancel(false);
		delayVerifier.close();
	}

	private void processBlocks() {
		if (!toProcess.isEmpty()) {
			try {
				lock.writeLock().lock();
				tryToProcessBlocks();
			} finally {
				lock.writeLock().unlock();
			}
		}
	}

	public void tryToProcessBlocks() {
		while (!toProcess.isEmpty()) {
			try {
				processBlock(toProcess.take());
			} catch (InterruptedException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	}


	private void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
		for (LedgerObserver observer : this.observers)
			observer.deliverNonFinalizedBlock(block, weight);
	}


}
