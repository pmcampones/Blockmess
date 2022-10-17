package ledger.blockchain;

import ledger.Ledger;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import main.GlobalProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import validators.ApplicationObliviousValidator;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Blockchain implements Ledger {

	private static final Logger logger = LogManager.getLogger(Blockchain.class.getName());

	private final List<LedgerObserver> observers = new LinkedList<>();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private final BlockFinalizer blockFinalizer;

	private final BlockScheduler blockScheduler;

	private final Map<UUID, BlockmessBlock> scheduledBlocks = new HashMap<>();


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
		this.blockFinalizer = new BlockFinalizer(genesisId);
		this.blockScheduler = new BlockScheduler();
	}

	private void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
		for (LedgerObserver observer : this.observers)
			observer.deliverNonFinalizedBlock(block, weight);
	}

	@Override
	public void submitBlock(BlockmessBlock block) {
		logger.debug("Received block {}, referencing {}", block.getBlockId(), block.getPrevRefs());
		processBlock(block);
	}

	private void processBlock(BlockmessBlock block) {
		long start = System.currentTimeMillis();
		List<UUID> prev = block.getPrevRefs();
		if (prev.size() != 1) {
			logger.info("Received malformed block with id: {}", block.getBlockId());
		} else if (!ApplicationObliviousValidator.getSingleton().isBlockValid(block)) {
			logger.info("Received invalid block {} referencing {}",
					block.getBlockId(), block.getPrevRefs().get(0));
		} else if (!isOrdered(block)) {
			submitUnorderedBlock(block, prev);
		} else {
			processOrderedBlocks(block, prev);
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

	private boolean isOrdered(BlockmessBlock block) {
		try {
			lock.readLock().lock();
			return blockFinalizer.hasBlock(block.getPrevRefs().get(0));
		} finally {
			lock.readLock().unlock();
		}
	}

	private void processOrderedBlocks(BlockmessBlock block, List<UUID> prev) {
		var scheduledBlock = new ScheduledBlock(block.getBlockId(), Set.of(prev.get(0)));
		List<ScheduledBlock> validOrderBlocks = blockScheduler.getValidOrdering(scheduledBlock);
		try {
			lock.writeLock().lock();
			validOrderBlocks.stream().map(ScheduledBlock::getId).map(scheduledBlocks::get)
					.forEach(this::processValidBlock);
		} finally {
			lock.writeLock().unlock();
		}
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

	private void submitUnorderedBlock(BlockmessBlock block, List<UUID> prev) {
		logger.info("Received unordered block with id: {}, referencing {}",
				block.getBlockId(), prev.get(0));
		Set<UUID> missingPrevious = Set.of(prev.get(0));
		var scheduledBlock = new ScheduledBlock(block.getBlockId(), missingPrevious);
		blockScheduler.submitUnorderedBlock(scheduledBlock, missingPrevious);
		scheduledBlocks.put(scheduledBlock.getId(), block);
	}

	@Override
	public void attachObserver(LedgerObserver observer) {
		this.observers.add(observer);
	}

	public Set<UUID> getFollowing(UUID block, int distance) {
		try {
			lock.readLock().lock();
			return blockFinalizer.getFollowing(block, distance);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public int getWeight(UUID block) throws IllegalArgumentException {
		try {
			lock.readLock().lock();
			return blockFinalizer.getWeight(block);
		} finally {
			lock.readLock().unlock();
		}
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


}
