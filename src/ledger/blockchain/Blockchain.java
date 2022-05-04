package ledger.blockchain;

import catecoin.blocks.chunks.MempoolChunk;
import catecoin.mempoolManager.BootstrapModule;
import catecoin.txs.IndexableContent;
import catecoin.validators.BlockValidator;
import ledger.DebugLedger;
import ledger.LedgerObserver;
import ledger.PrototypicalLedger;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.util.stream.Collectors.*;

public class Blockchain<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>, ? extends SybilElectionProof>>
        implements PrototypicalLedger<B>, DebugLedger<B> {

    private static final Logger logger = LogManager.getLogger(Blockchain.class.getName());

    private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors() / 4;

    private static final long WAIT_DELAY_REORDER = 1000 * 5;

    private static final long VERIFICATION_INTERVAL = WAIT_DELAY_REORDER * 5;

    private static final int FINALIZED_WEIGHT = 6;

    private static final ScheduledThreadPoolExecutor pool = initPool();

    private static ScheduledThreadPoolExecutor initPool() {
        ScheduledThreadPoolExecutor pool = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(POOL_SIZE);
        pool.setRemoveOnCancelPolicy(true);
        return pool;
    }

    private final BlockValidator<B> validator;


    public final int finalizedWeight;

    /**
     * Properties are kept to be issued in the cloning process of the Ledger prototype.
     */
    private final Properties props;

    private final BootstrapModule bootstrapModule;

    //Blocks that have arrived but are yet to be processed.
    //This Blockchain implementation is fundamentally serial.
    //Blocks are kept in this queue and a single thread will process each individually in a FIFO order.
    private final BlockingQueue<B> toProcess = new LinkedBlockingQueue<>();

    //Collection containing the blocks that have no other block referencing them.
    //Effectively being the tips of their forks.
    private final Map<UUID, BlockchainNode> chainTips = new HashMap<>();

    //Kept public to be accessed by the tests, this is not used elsewhere.
    public final Map<UUID, BlockchainNode> blocks = new HashMap<>();

    //Only used in the unit tests
    public final Map<UUID, BlockchainNode> finalized = new HashMap<>();

    private final List<LedgerObserver<B>> observers = new LinkedList<>();

    private final DelayVerifier<B> delayVerifier;

    private BlockchainNode lastFinalized;

    private final ScheduledFuture<?> task;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public Blockchain(Properties props, BlockValidator<B> validator,
                      BootstrapModule bootstrapModule)  {
        this(props, validator, bootstrapModule, computeGenesisUUID(props));
    }

    private static UUID computeGenesisUUID(Properties props) {
        String genesisUUIDStr = props.getProperty("genesisUUID",
                "00000000-0000-0000-0000-000000000000");
        return UUID.fromString(genesisUUIDStr);
    }

    public Blockchain(Properties props, BlockValidator<B> validator,
                      BootstrapModule bootstrapModule, UUID genesisUUID) {
        this.props = props;
        this.bootstrapModule = bootstrapModule;
        this.finalizedWeight = computeFinalizedWeight(props);
        this.validator = validator;
        createGenesisBlock(genesisUUID);
        bootstrapBlockchain(bootstrapModule.getStoredChunks(props));
        this.delayVerifier = generateDelayVerifier(props);
        int expectedTimeBetweenBlocks = Integer.parseInt(props.getProperty("expectedTimeBetweenBlocks"));
        this.task = pool.scheduleAtFixedRate(new QueuePoller(), expectedTimeBetweenBlocks / 5, expectedTimeBetweenBlocks / 5, TimeUnit.MILLISECONDS);

    }

    public static int computeFinalizedWeight(Properties props) {
        return parseInt(props.getProperty("finalizedWeight",
                String.valueOf(FINALIZED_WEIGHT)));
    }

    private DelayVerifier<B> generateDelayVerifier(Properties props) {
        long waitDelayReorder = parseLong(props.getProperty("waitDelayReorder",
                String.valueOf(WAIT_DELAY_REORDER)));
        long verificationDelay = parseLong(props.getProperty("verificationDelay",
                String.valueOf(VERIFICATION_INTERVAL)));
        return new DelayVerifier<>(waitDelayReorder, verificationDelay, this);
    }

    private void createGenesisBlock(UUID genesisUUID) {
        BlockchainNode genesis = new BlockchainNode(genesisUUID,
                Collections.emptySet(), finalizedWeight);
        chainTips.put(genesis.getBlockId(), genesis);
        blocks.put(genesis.getBlockId(), genesis); finalized.put(genesis.getBlockId(), genesis);
        lastFinalized = genesis;
    }

        private void bootstrapBlockchain(List<MempoolChunk> chunks) {
        for (MempoolChunk chunk : chunks) {
            BlockchainNode block = new BlockchainNode(chunk.getId(), chunk.getPreviousChunksIds(),
                    chunk.getInherentWeight());
            block.getPrevious().forEach(chainTips::remove);
            block.getPrevious().stream().map(blocks::get)
                    .forEach(b -> b.getFollowing().add(block.getBlockId()));
            chainTips.put(block.getBlockId(), block);
            blocks.put(block.getBlockId(), block);
            finalized.put(block.getBlockId(), block);
            lastFinalized = block;
        }
    }

    //DO NOT PLACE LOCK HERE: DEADLOCK WILL ENSUE
    //THERE IS A CYCLIC DEPENDENCY BETWEEN THIS CLASS AND THE DELAYVERIFIER (sorry)
    boolean hasBlock(UUID blockId) {
        return blocks.containsKey(blockId);
    }

    @Override
    public void submitBlock(B block) {
        toProcess.add(block);
        processBlocks();
    }

    @Override
    public void attachObserver(LedgerObserver<B> observer) {
        this.observers.add(observer);
    }

    @Override
    public PrototypicalLedger<B> clonePrototype(UUID genesisId) {
        return new Blockchain<>(props, validator, bootstrapModule, genesisId);
    }

    @Override
    public int getFinalizedWeight() {
        return finalizedWeight;
    }

    @Override
    public Set<UUID> getFinalizedIds() {
        return Set.copyOf(finalized.keySet());
    }

    @Override
    public Set<UUID> getNodesIds() {
        return Set.copyOf(blocks.keySet());
    }

    @Override
    public Set<UUID> getForkBlocks(int depth) throws IllegalArgumentException {
        if (depth < 0)
            throw new IllegalArgumentException();
        return getForkBlocks(getBlockR(), depth);
    }

    private Set<UUID> getForkBlocks(Set<UUID> curr, int depth) {
        if (depth == 0)
            return curr;
        Set<UUID> prev = curr.stream()
                .map(blocks::get)
                .map(BlockchainNode::getPrevious)
                .flatMap(Collection::stream)
                .collect(toSet());
        return getForkBlocks(prev, depth - 1);
    }

    private class QueuePoller extends Thread {

        public void run() {
            toProcess.addAll(delayVerifier.getOrderedBlocks());
            processBlocks();
        }

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

    private void processBlock(B block) {
        long start = System.currentTimeMillis();
        List<UUID> prev = block.getPrevRefs();
        if (prev.size() != 1) {
            logger.info("Received malformed block with id: {}", block.getBlockId());
        } else if (!blocks.containsKey(prev.get(0))) {
            logger.info("Received unordered block with id: {}, referencing {}",
                    block.getBlockId(), block.getPrevRefs().get(0));
            delayVerifier.submitUnordered(block);
        } else if (validator.isBlockValid(block)) {
            processValidBlock(block, prev);
        } else {
            logger.info("Received invalid block {} referencing {}",
                    block.getBlockId(), block.getPrevRefs().get(0));
        }
        long end = System.currentTimeMillis();
        logger.info("Elapsed time in processing block {}: {} miliseconds",
                block.getBlockId(), (end - start));
    }

    private void processValidBlock(B block, List<UUID> prev) {
        logger.debug("Processing valid block {}", block.getBlockId());
        int weight = blocks.get(prev.get(0)).getWeight() + block.getInherentWeight();
        deliverNonFinalizedBlocks(block, weight);
        addBlock(new BlockchainNode(block.getBlockId(),
                new HashSet<>(prev), weight));
    }

    private void deliverNonFinalizedBlocks(B block, int weight) {
        for (LedgerObserver<B> observer : this.observers)
            observer.deliverNonFinalizedBlock(block, weight);
    }

    private void addBlock(BlockchainNode block) {
        BlockchainNode prev = blocks.get(block.getPrevious().iterator().next());
        prev.getFollowing().add(block.getBlockId());
        chainTips.remove(prev.getBlockId());
        chainTips.put(block.getBlockId(), block);
        logger.debug("Inserting: {}", block.getBlockId());
        blocks.put(block.getBlockId(), block);
        finalized.putAll(finalizeBlocks().stream().collect(toMap(BlockchainNode::getBlockId, b -> b)));
    }

    private List<BlockchainNode> finalizeBlocks() {
        Set<UUID> deleted = deleteForkedChains();
        List<BlockchainNode> finalized = finalizeForward();
        List<UUID> finalizedIds = finalized.stream().map(BlockchainNode::getBlockId).collect(toList());
        deliverFinalizedBlocks(finalizedIds, deleted);
        return finalized;
    }

    private void deliverFinalizedBlocks(List<UUID> finalizedIds, Set<UUID> deleted) {
        for (LedgerObserver<B> observer : this.observers)
            observer.deliverFinalizedBlocks(finalizedIds, deleted);
    }

    /*
     * The collect operation is required because otherwise we'd be removing elements from the same DS that is being iterated
     */
    private Set<UUID> deleteForkedChains() {
        assert !chainTips.isEmpty();
        int maxChainWeight = chainTips.values().stream()
                .mapToInt(BlockchainNode::getWeight).max()
                .getAsInt();
        List<BlockchainNode> toRemoveTips = chainTips.values().stream()
                .filter(b -> maxChainWeight - b.getWeight() >= finalizedWeight)
                .collect(toList());
        Set<UUID> deletedBlocks = new HashSet<>();
        for (BlockchainNode tip : toRemoveTips)
            deletedBlocks.addAll(deleteForkedChain(tip));
        return deletedBlocks;
    }

    private Set<UUID> deleteForkedChain(BlockchainNode chainTip) {
        Set<UUID> deleted = new HashSet<>();
        chainTips.remove(chainTip.getBlockId());
        BlockchainNode currentBlock = chainTip;
        BlockchainNode previousBlock = null;
        while(currentBlock.getFollowing().size() < 2) {
            blocks.remove(currentBlock.getBlockId());
            deleted.add(currentBlock.getBlockId());
            logger.debug("Removing: {}", currentBlock.getBlockId());
            previousBlock = currentBlock;
            currentBlock = blocks.get(currentBlock.getPrevious().iterator().next());
        }
        assert previousBlock != null;
        currentBlock.getFollowing().remove(previousBlock.getBlockId());
        return deleted;
    }

    private List<BlockchainNode> finalizeForward() {
        assert !chainTips.isEmpty();
        int maxWeight = chainTips.values().stream()
                .mapToInt(BlockchainNode::getWeight).max().getAsInt();
        List<BlockchainNode> newlyFinalized = new LinkedList<>();
        if (lastFinalized.getFollowing().size() < 2
                && maxWeight - blocks.get(lastFinalized.getFollowing().iterator().next()).getWeight() >= finalizedWeight)
            do {
                lastFinalized = blocks.get(lastFinalized.getFollowing().iterator().next());
                logger.debug("Finalizing: {}", lastFinalized.getBlockId());
                newlyFinalized.add(lastFinalized);
            } while (lastFinalized.getFollowing().size() < 2 && maxWeight - lastFinalized.getWeight() > finalizedWeight);

        return newlyFinalized;
    }

    public Set<UUID> getFollowing(UUID block, int distance) {
        if (distance < 0 || blocks.get(block) == null)
            throw new IllegalArgumentException();
        return distance == 0 ? Set.of(block) :
                blocks.get(block)
                        .getFollowing()
                        .stream()
                        .map(b -> getFollowing(b, distance - 1))
                        .flatMap(Collection::stream)
                        .collect(toSet());
    }

    @Override
    public int getWeight(UUID block) throws IllegalArgumentException {
        BlockchainNode node = blocks.get(block);
        if (node == null) throw new IllegalArgumentException();
        return node.getWeight();
    }

    @Override
    public boolean isInLongestChain(UUID nodeId) {
        BlockchainNode node = blocks.get(nodeId);
        if (node == null)
            return false;
        if (chainTips.containsKey(nodeId))
            return node.getWeight() == getMaxWeight();
        return node.getFollowing().stream().anyMatch(this::isInLongestChain);
    }

    @Override
    public void close() {
        task.cancel(false);
        delayVerifier.close();
    }

    @Override
    public Set<UUID> getBlockR() {
        assert !chainTips.isEmpty();
        try {
            lock.readLock().lock();
            BlockchainNode maxB = chainTips.values().stream().max((b1, b2) -> {
                int dif = b1.getWeight() - b2.getWeight();
                return dif == 0 ? 1 : dif;
            }).get();
            return Set.of(maxB.getBlockId());
        } finally {
            lock.readLock().unlock();
        }
    }

    private int getMaxWeight() {
        try {
            lock.readLock().lock();
            return chainTips.values().stream()
                    .mapToInt(BlockchainNode::getWeight)
                    .max().orElse(0);
        } finally {
            lock.readLock().unlock();
        }
    }
}
