package ledger.blockchain;

import applicationInterface.GlobalProperties;
import ledger.Ledger;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import validators.FixedApplicationObliviousValidator;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * A blockchain implementation of the ledger interface.
 * <p>The blockchain is a directed acyclic graph of blocks, where each block has a unique id and a list of previous block ids.</p>
 * <p>This structure receives blocks from the network, validates them and delivers them to the application in a deterministic order, common to all nodes.</p>
 */
public class Blockchain implements Ledger {

	private static final Logger logger = LogManager.getLogger(Blockchain.class.getName());

	private final List<LedgerObserver> observers = new LinkedList<>();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private final BlockFinalizer blockFinalizer;

	private final BlockScheduler blockScheduler;

	//Blocks that have been received but are not yet ordered.
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

	/**
	 * Adds a block to the blockchain.
	 * <p>Blocks are added to the blockchain in a non-deterministic order, as they are received from the network.</p>
	 * <p>Before adding a block to the blockchain, it is validated by the application and partially ordered.</p>
	 * @param block A signed block ready to be accepted by the application.
	 */
	@Override
	public void submitBlock(BlockmessBlock block) {
		logger.debug("Received block {}, referencing {}", block.getBlockId(), block.getPrevRefs());
		long start = System.currentTimeMillis();
		List<UUID> prev = block.getPrevRefs();
		if (prev.size() != 1) {
			logger.info("Received malformed block with id: {}", block.getBlockId());
		} else if (!FixedApplicationObliviousValidator.getSingleton().isBlockValid(block)) {
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

	private void deliverFinalizedBlocks(List<UUID> finalizedIds, Set<UUID> deleted) {
		for (LedgerObserver observer : this.observers)
			observer.deliverFinalizedBlocks(finalizedIds, deleted);
	}

	private void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
		for (LedgerObserver observer : this.observers)
			observer.deliverNonFinalizedBlock(block, weight);
	}

	/**
	 * Verifies if a block is ordered.
	 * <p>A block is ordered if its parent block is already in the blockchain.</p>
	 */
	private boolean isOrdered(BlockmessBlock block) {
		try {
			lock.readLock().lock();
			return blockFinalizer.hasBlock(block.getPrevRefs().get(0));
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Adds a block to the blockchain.
	 * <p>If this block has children wanting for its arrival, they are added to the blockchain as well.
	 * Following a valid ordering where no block is delivered before its parent.</p>
	 */
	private void processOrderedBlocks(BlockmessBlock block, List<UUID> prev) {
		scheduledBlocks.put(block.getBlockId(), block);
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

	/**
	 * Adds a block to the blockchain, and delivers it "unconfirmed" to the application.
	 * <p>In this process, identifies and delivers finalized and pruned blocks to the application.</p>
	 */
	private void processValidBlock(BlockmessBlock block) {
		List<UUID> prev = block.getPrevRefs();
		logger.debug("Processing valid block {}", block.getBlockId());
		int weight = blockFinalizer.getWeight(prev.get(0)) + block.getInherentWeight();//blocks.get(prev.get(0)).getWeight() + block.getInherentWeight();
		var deliverFinalizedInfo = blockFinalizer.addBlock(block.getBlockId(),
				new HashSet<>(prev), block.getInherentWeight());
		Stream.concat(deliverFinalizedInfo.getLeft().stream(), deliverFinalizedInfo.getRight().stream())
				.forEach(scheduledBlocks::remove);
		deliverFinalizedBlocks(deliverFinalizedInfo.getLeft(), deliverFinalizedInfo.getRight());
		deliverNonFinalizedBlock(block, weight);
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
