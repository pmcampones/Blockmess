package ledger.ledgerManager;

import catecoin.blockConstructors.ComposableContentStorageImp;
import catecoin.blockConstructors.ContentStorage;
import catecoin.txs.Transaction;
import ledger.Ledger;
import ledger.LedgerObserver;
import ledger.blockchain.Blockchain;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.exceptions.LedgerTreeNodeDoesNotExistException;
import ledger.ledgerManager.nodes.BlockmessChain;
import ledger.ledgerManager.nodes.ParentTreeNode;
import ledger.ledgerManager.nodes.ReferenceNode;
import ledger.prototype.PrototypeHasNotBeenDefinedException;
import logsGenerators.ChangesInNumberOfChainsLog;
import main.GlobalProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class LedgerManager
        implements ParentTreeNode,
        Ledger<BlockmessBlock>, LedgerObserver<BlockmessBlock>, ContentStorage<StructuredValue<Transaction>> {

    private static final Logger logger = LogManager.getLogger(LedgerManager.class);

    private final Map<UUID, BlockmessChain> chains = Collections.synchronizedMap(new LinkedHashMap<>());

    private final BlockingQueue<BlockmessChain> toCreateChains = new LinkedBlockingQueue<>();

    private final BlockingQueue<UUID> toRemoveChains = new LinkedBlockingQueue<>();

    private final List<LedgerObserver<BlockmessBlock>> observers = new LinkedList<>();

    private final int finalizedWeight;

    private final int minNumChains;

    private final int maxNumChains;

    private long confirmBar = 0;

    private final BlockingQueue<Object> deliverFinalizedRequests = new LinkedBlockingQueue<>();

    public final List<ChangesInNumberOfChainsLog> changesLog = new LinkedList<>();
    
    public LedgerManager() throws PrototypeHasNotBeenDefinedException {
        this(computeOgChainId());
    }

    private static UUID computeOgChainId() {
        Properties props = GlobalProperties.getProps();
        String ogChainIdStr = props.getProperty("ogChainId",
                "00000000-0000-0000-0000-000000000000");
        return UUID.fromString(ogChainIdStr);
    }

    public LedgerManager(UUID ogChainId) throws PrototypeHasNotBeenDefinedException {
        Properties props = GlobalProperties.getProps();
        var originChain = new ReferenceNode(props, ogChainId, this, 0, 1, 0,
                new ComposableContentStorageImp<>());
        originChain.attachObserver(this);
        this.minNumChains = parseInt(props.getProperty("minNumChains", "1"));
        this.maxNumChains = parseInt(props.getProperty("maxNumChains", String.valueOf(Integer.MAX_VALUE)));
        int initialNumChains = parseInt(props.getProperty("initialNumChains", String.valueOf(minNumChains)));
        initializeChains(originChain, initialNumChains);
        this.finalizedWeight = parseInt(props.getProperty("finalizedWeight", String.valueOf(Blockchain.FINALIZED_WEIGHT)));
        new Thread(this::processBlockDeliveries).start();
    }

    private void initializeChains(BlockmessChain origin, int initialNumChains)
            throws PrototypeHasNotBeenDefinedException {
        int seedCounter = 1;
        this.chains.put(origin.getChainId(), origin);
        List<BlockmessChain> prevRoundChains = List.of(origin);
        while(chains.size() < initialNumChains) {
            Iterator<BlockmessChain> chainIterator = prevRoundChains.iterator();
            while(chainIterator.hasNext() && chains.size() + toCreateChains.size() < initialNumChains) {
                BlockmessChain chain = chainIterator.next();
                chain.spawnPermanentChildren(getRandomUUIDFromSeed(seedCounter++),
                        getRandomUUIDFromSeed(seedCounter++));
            }
            prevRoundChains = new ArrayList<>(chains.values());
            addNewChains();
        }
    }

    private UUID getRandomUUIDFromSeed(int seed) {
        Random r = new Random(seed);
        return new UUID(r.nextLong(), r.nextLong());
    }
    
    private void processBlockDeliveries() {
        while(true) {
            try {
                deliverFinalizedRequests.take();
                deliverFinalizedRequests.clear();
                deliverFinalizedBlocksAsync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Set<UUID> getBlockR() {
        return chains.values().iterator().next().getBlockR();
    }

    private void addNewChains() {
        while(!toCreateChains.isEmpty()) {
            try {
                BlockmessChain chain = toCreateChains.take();
                chains.put(chain.getChainId(), chain);
                chain.attachObserver(this);
                logger.info("Creating Chain: {}", chain.getChainId());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void submitBlock(BlockmessBlock block) {
        UUID destinationChain = block.getDestinationChain();
        chains.get(destinationChain).submitBlock(block);
    }

    @Override
    public Set<UUID> getFollowing(UUID block, int distance) throws IllegalArgumentException {
        return chains.entrySet().iterator().next().getValue().getFollowing(block, distance);
    }

    @Override
    public int getWeight(UUID block) throws IllegalArgumentException {
        return chains.values().stream()
                .map(b -> {try {
                    return b.getWeight(block);
                } catch (IllegalArgumentException e) {
                    return null;
                }}).filter(Objects::nonNull).mapToInt(i -> i).iterator().next();
    }

    @Override
    public boolean isInLongestChain(UUID nodeId) {
        return chains.values().stream().anyMatch(b -> b.isInLongestChain(nodeId));
    }

    @Override
    public void close() {
        chains.values().forEach(Ledger::close);
    }

    @Override
    public void attachObserver(LedgerObserver<BlockmessBlock> observer) {
        this.observers.add(observer);
    }

    @Override
    public void forgetUnconfirmedChains(Set<UUID> discartedChainsIds) {
        toRemoveChains.addAll(discartedChainsIds);
    }

    @Override
    public void replaceChild(BlockmessChain newChild) {}

    @Override
    public void createChains(List<BlockmessChain> createdChains) {
        toCreateChains.addAll(createdChains);
    }

    @Override
    public ParentTreeNode getTreeRoot() {
        return this;
    }

    @Override
    public void deliverNonFinalizedBlock(BlockmessBlock nonFinalized, int weight) {
        for (LedgerObserver<BlockmessBlock> observer : observers)
            observer.deliverNonFinalizedBlock(nonFinalized, weight);
    }

    @Override
    public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
        tryToPlaceFinalizationRequest();
        for (var observer : observers)
            observer.deliverFinalizedBlocks(emptyList(), discarded);
    }

    private void tryToPlaceFinalizationRequest() {
        try {
            deliverFinalizedRequests.put(new Object());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void deliverFinalizedBlocksAsync() {
        int initialNumChains = chains.size();
        removeObsoleteChains();
        addNewChains();
        int finalNumChains = chains.size();
        if (finalNumChains != initialNumChains)
            changesLog.forEach(obs -> obs.logChangeInNumChains(finalNumChains));
        System.out.println();
        for (BlockmessChain chain : chains.values())
            logger.debug("Chain {} has {} finalized blocks pending, minNextRank is {}, next block has rank {}",
                    chain.getChainId(), chain.getNumFinalizedPending(), chain.getNextRank(),
                    chain.hasFinalized() ? chain.peekFinalized().getBlockRank() : -1);
        List<BlockmessBlock> linearizedFinalized = linearizeFinalizedBlocksInChains();
        assert linearizedFinalized != null;
        List<UUID> linearizedUUID = linearizedFinalized.stream().map(BlockmessBlock::getBlockId).collect(toList());
        for (var observer : observers)
            observer.deliverFinalizedBlocks(linearizedUUID, emptySet());
    }

    private List<BlockmessBlock> linearizeFinalizedBlocksInChains() {
        try {
            return tryToLinearizeFinalizedBlocksInChains();
        } catch (LedgerTreeNodeDoesNotExistException | PrototypeHasNotBeenDefinedException e) {
            e.printStackTrace();
        }
        return emptyList();
    }

    private void removeObsoleteChains() {
        while (!toRemoveChains.isEmpty()) {
            try {
                chains.remove(toRemoveChains.take()).close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private List<BlockmessBlock> tryToLinearizeFinalizedBlocksInChains()
            throws LedgerTreeNodeDoesNotExistException, PrototypeHasNotBeenDefinedException {
        List<BlockmessBlock> res = new LinkedList<>();
        List<BlockmessBlock> confirmed;
        do {
            confirmBar = computeNextConfirmBar();
            confirmed = confirmBlocksWithRankEqualToConfirmationBar();
            res.addAll(confirmed);
        } while (!confirmed.isEmpty());
        return res;
    }

    private List<BlockmessBlock> confirmBlocksWithRankEqualToConfirmationBar()
            throws PrototypeHasNotBeenDefinedException, LedgerTreeNodeDoesNotExistException {
        List<BlockmessBlock> res = new LinkedList<>();
        Iterator<BlockmessChain> chainIterator = chains.values().iterator();
        while (chainIterator.hasNext()) {
            Optional<BlockmessChain> chainOp = findNextChainToDeliver(chainIterator);
            if (chainOp.isPresent()) {
                BlockmessChain chain = chainOp.get();
                BlockmessBlock nextDeliver = chain.deliverChainBlock();
                res.add(nextDeliver);
                logger.info("Finalizing block {} in Chain {}", nextDeliver.getBlockId(), chain.getChainId());
                logger.debug("Number overloaded recent finalized blocks: {}", chain.getNumOverloaded());
                logger.debug("Number underloaded recent finalized blocks: {}", chain.getNumUnderloaded());
                if (chain.shouldSpawn() && countReferencedPermanent() <= maxNumChains - 2)
                    chain.spawnChildren(nextDeliver.getBlockId());
                else {
                    discardSuccessiveChains();
                    chainIterator = chains.values().iterator();
                }
            }
        }
        return res;
    }

    private void discardSuccessiveChains() throws LedgerTreeNodeDoesNotExistException {
        int initialNumChains = chains.size();
        boolean mergeFound = true;
        int numCanRemove = getAvailableChains().size() - minNumChains;
        List<BlockmessChain> toResetSamples = new LinkedList<>();
        while (numCanRemove > 0 && mergeFound) {
            mergeFound = false;
            List<UUID> toRemove = new LinkedList<>();
            for (BlockmessChain chain : chains.values())
                if (chain.shouldMerge() && numCanRemove > 0) {
                    toResetSamples.add(chain);
                    Set<UUID> merged = chain.mergeChildren();
                    numCanRemove -= merged.size();
                    toRemove.addAll(merged);
                    System.out.println("Merging Chains: " + toRemove);
                    mergeFound = true;
                }
            toRemove.forEach(chains::remove);
        }
        toResetSamples.forEach(BlockmessChain::resetSamples);
        int finalNumChains = chains.size();
        if (finalNumChains != initialNumChains)
            changesLog.forEach(obs -> obs.logChangeInNumChains(finalNumChains));
    }

    public List<BlockmessChain> getAvailableChains() {
        return tryToGetAvailableChains();
    }

    @NotNull
    private List<BlockmessChain> tryToGetAvailableChains() {
        List<BlockmessChain> allChains = List.copyOf(chains.values());
        Set<UUID> preferable = getOrigin().getPriorityChains().stream()
                .map(BlockmessChain::getChainId).collect(toSet());
        return allChains.stream()
                .filter(b -> preferable.contains(b.getChainId()))
                .collect(toList());
    }

    private long computeNextConfirmBar() {
        OptionalLong min = chains.values().stream()
                .mapToLong(BlockmessChain::getNextRank)
                .min();
        return min.orElse(confirmBar);
    }

    @Override
    public int getFinalizedWeight() {
        return finalizedWeight;
    }

    @Override
    public Set<UUID> getForkBlocks(int depth) {
        return chains.values().iterator().next().getForkBlocks(1);
    }

    public BlockmessChain getOrigin() {
        return chains.entrySet().iterator().next().getValue();
    }

    private Optional<BlockmessChain> findNextChainToDeliver(Iterator<BlockmessChain> chainIterator) {
        while (chainIterator.hasNext()) {
            BlockmessChain chain = chainIterator.next();
            BlockmessBlock headBlock = chain.peekFinalized();
            if (headBlock != null && headBlock.getBlockRank() < confirmBar)
                return Optional.of(chain);
        }
        return Optional.empty();
    }

    public long getHighestSeenRank() {
        long maxRank = Long.MIN_VALUE;
        for (BlockmessChain chain : chains.values()) {
            long ChainMaxRank = chain.getRankFromRefs(chain.getBlockR());
            maxRank = Math.max(maxRank, ChainMaxRank);
        }
        return maxRank;
    }

    @Override
    public List<StructuredValue<Transaction>> generateContentListList(Collection<UUID> states, int usedSpace)
            throws IOException {
        return getOrigin().generateContentListList(states, usedSpace);
    }

    @Override
    public List<StructuredValue<Transaction>> generateBoundContentListList(Collection<UUID> states, int usedSpace, int maxTxs)
            throws IOException {
        return getOrigin().generateBoundContentListList(states, usedSpace, maxTxs);
    }

    @Override
    public void submitContent(Collection<StructuredValue<Transaction>> content) {
        getOrigin().submitContent(content);
    }

    @Override
    public void submitContent(StructuredValue<Transaction> content) {
        getOrigin().submitContent(content);
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        getOrigin().deleteContent(contentIds);
    }

    @Override
    public Collection<StructuredValue<Transaction>> getStoredContent() {
        return chains.values().stream()
                .map(ContentStorage::getStoredContent)
                .flatMap(Collection::stream)
                .collect(toList());
    }

    @Override
    public void halveChainThroughput() {}

    @Override
    public void doubleChainThroughput() {}

    @Override
    public int getThroughputReduction() {
        return 0;
    }

    @Override
    public void setChainThroughputReduction(int reduction) {}

    private int countReferencedPermanent() {
        return chains.values().stream().mapToInt(BlockmessChain::countReferencedPermanent).sum() + 1;
    }
}
