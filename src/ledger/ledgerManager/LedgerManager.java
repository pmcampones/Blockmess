package ledger.ledgerManager;

import catecoin.blockConstructors.ComposableContentStorageImp;
import catecoin.blockConstructors.ContentStorage;
import catecoin.blocks.ContentList;
import catecoin.txs.IndexableContent;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import sybilResistantElection.SybilElectionProof;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class LedgerManager<E extends IndexableContent, C extends ContentList<StructuredValue<E>>, P extends SybilElectionProof>
        implements ParentTreeNode<E,C,P>,
        Ledger<BlockmessBlock<C,P>>, LedgerObserver<BlockmessBlock<C,P>>, ContentStorage<StructuredValue<E>> {

    private static final Logger logger = LogManager.getLogger(LedgerManager.class);

    private final Map<UUID, BlockmessChain<E,C,P>> chains = Collections.synchronizedMap(new LinkedHashMap<>());

    private final BlockingQueue<BlockmessChain<E,C,P>> toCreateChains = new LinkedBlockingQueue<>();

    private final BlockingQueue<UUID> toRemoveChains = new LinkedBlockingQueue<>();

    private final List<LedgerObserver<BlockmessBlock<C,P>>> observers = new LinkedList<>();

    private final int finalizedWeight;

    private final int minNumChains;

    private final int maxNumChains;

    private long confirmBar = 0;

    private final BlockingQueue<Object> deliverFinalizedRequests = new LinkedBlockingQueue<>();

    public final List<ChangesInNumberOfChainsLog> changesLog = new LinkedList<>();
    
    public LedgerManager(Properties props) throws PrototypeHasNotBeenDefinedException {
        this(props, computeOgChainId(props));
    }

    private static UUID computeOgChainId(Properties props) {
        String ogChainIdStr = props.getProperty("ogChainId",
                "00000000-0000-0000-0000-000000000000");
        return UUID.fromString(ogChainIdStr);
    }

    public LedgerManager(Properties props, UUID ogChainId) throws PrototypeHasNotBeenDefinedException {
        var originChain = new ReferenceNode<>(props, ogChainId, this, 0, 1, 0,
                new ComposableContentStorageImp<>());
        originChain.attachObserver(this);
        this.minNumChains = parseInt(props.getProperty("minNumChains", "1"));
        this.maxNumChains = parseInt(props.getProperty("maxNumChains",
                String.valueOf(Integer.MAX_VALUE)));
        int initialNumChains = parseInt(props.getProperty("initialNumChains",
                String.valueOf(minNumChains)));
        initializeChains(originChain, initialNumChains);
        this.finalizedWeight = Blockchain.computeFinalizedWeight(props);
        new Thread(this::processBlockDeliveries).start();
    }

    private void initializeChains(BlockmessChain<E,C,P> origin, int initialNumChains)
            throws PrototypeHasNotBeenDefinedException {
        int seedCounter = 1;
        this.chains.put(origin.getChainId(), origin);
        List<BlockmessChain<E,C,P>> prevRoundChains = List.of(origin);
        while(chains.size() < initialNumChains) {
            Iterator<BlockmessChain<E,C,P>> ChainIterator = prevRoundChains.iterator();
            while(ChainIterator.hasNext() && chains.size() + toCreateChains.size() < initialNumChains) {
                BlockmessChain<E,C,P> Chain = ChainIterator.next();
                Chain.spawnPermanentChildren(getRandomUUIDFromSeed(seedCounter++),
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

    @Override
    public void submitBlock(BlockmessBlock<C,P> block) {
        UUID destinationChain = block.getDestinationChain();
        chains.get(destinationChain).submitBlock(block);
    }

    @Override
    public void attachObserver(LedgerObserver<BlockmessBlock<C,P>> observer) {
        this.observers.add(observer);
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
    public void replaceChild(BlockmessChain<E,C,P> newChild) {}

    @Override
    public void forgetUnconfirmedChains(Set<UUID> discartedChainsIds) {
        toRemoveChains.addAll(discartedChainsIds);
    }

    @Override
    public void createChains(List<BlockmessChain<E,C,P>> createdChains) {
        toCreateChains.addAll(createdChains);
    }

    @Override
    public ParentTreeNode<E,C,P> getTreeRoot() {
        return this;
    }

    public BlockmessChain<E,C,P> getOrigin() {
        return chains.entrySet().iterator().next().getValue();
    }

    @Override
    public void deliverNonFinalizedBlock(BlockmessBlock<C, P> nonFinalized, int weight) {
        for (LedgerObserver<BlockmessBlock<C,P>> observer : observers)
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
        for (BlockmessChain<E,C,P> chain : chains.values())
            logger.debug("Chain {} has {} finalized blocks pending, minNextRank is {}, next block has rank {}",
                    chain.getChainId(), chain.getNumFinalizedPending(), chain.getNextRank(),
                    chain.hasFinalized() ? chain.peekFinalized().getBlockRank() : -1);
        List<BlockmessBlock<C,P>> linearizedFinalized = linearizeFinalizedBlocksInChains();
        assert linearizedFinalized != null;
        List<UUID> linearizedUUID = linearizedFinalized.stream().map(BlockmessBlock::getBlockId).collect(toList());
        for (var observer : observers)
            observer.deliverFinalizedBlocks(linearizedUUID, emptySet());
    }

    private void addNewChains() {
        while(!toCreateChains.isEmpty()) {
            try {
                BlockmessChain<E,C,P> Chain = toCreateChains.take();
                chains.put(Chain.getChainId(), Chain);
                Chain.attachObserver(this);
                logger.info("Creating Chain: {}", Chain.getChainId());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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

    private List<BlockmessBlock<C,P>> linearizeFinalizedBlocksInChains() {
        try {
            return tryToLinearizeFinalizedBlocksInChains();
        } catch (LedgerTreeNodeDoesNotExistException | PrototypeHasNotBeenDefinedException e) {
            e.printStackTrace();
        }
        return emptyList();
    }

    private List<BlockmessBlock<C, P>> tryToLinearizeFinalizedBlocksInChains()
            throws LedgerTreeNodeDoesNotExistException, PrototypeHasNotBeenDefinedException {
        List<BlockmessBlock<C,P>> res = new LinkedList<>();
        List<BlockmessBlock<C,P>> confirmed;
        do {
            confirmBar = computeNextConfirmBar();
            confirmed = confirmBlocksWithRankEqualToConfirmationBar();
            res.addAll(confirmed);
        } while (!confirmed.isEmpty());
        return res;
    }

    private List<BlockmessBlock<C,P>> confirmBlocksWithRankEqualToConfirmationBar()
            throws PrototypeHasNotBeenDefinedException, LedgerTreeNodeDoesNotExistException {
        List<BlockmessBlock<C,P>> res = new LinkedList<>();
        Iterator<BlockmessChain<E,C,P>> ChainIterator = chains.values().iterator();
        while (ChainIterator.hasNext()) {
            Optional<BlockmessChain<E,C,P>> ChainOp = findNextChainToDeliver(ChainIterator);
            if (ChainOp.isPresent()) {
                BlockmessChain<E,C,P> chain = ChainOp.get();
                BlockmessBlock<C,P> nextDeliver = chain.deliverChainBlock();
                res.add(nextDeliver);
                logger.info("Finalizing block {} in Chain {}", nextDeliver.getBlockId(), chain.getChainId());
                logger.debug("Number overloaded recent finalized blocks: {}", chain.getNumOverloaded());
                logger.debug("Number underloaded recent finalized blocks: {}", chain.getNumUnderloaded());
                if (chain.shouldSpawn() && countReferencedPermanent() <= maxNumChains - 2)
                    chain.spawnChildren(nextDeliver.getBlockId());
                else {
                    discardSuccessiveChains();
                    ChainIterator = chains.values().iterator();
                }
            }
        }
        return res;
    }

    private void discardSuccessiveChains() throws LedgerTreeNodeDoesNotExistException {
        int initialNumChains = chains.size();
        boolean mergeFound = true;
        int numCanRemove = getAvailableChains().size() - minNumChains;
        List<BlockmessChain<E,C,P>> toResetSamples = new LinkedList<>();
        while (numCanRemove > 0 && mergeFound) {
            mergeFound = false;
            List<UUID> toRemove = new LinkedList<>();
            for (BlockmessChain<E,C,P> Chain : chains.values())
                if (Chain.shouldMerge() && numCanRemove > 0) {
                    toResetSamples.add(Chain);
                    Set<UUID> merged = Chain.mergeChildren();
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

    private Optional<BlockmessChain<E,C,P>> findNextChainToDeliver(Iterator<BlockmessChain<E,C,P>> ChainIterator) {
        while (ChainIterator.hasNext()) {
            BlockmessChain<E,C,P> Chain = ChainIterator.next();
            BlockmessBlock<C,P> headBlock = Chain.peekFinalized();
            if (headBlock != null && headBlock.getBlockRank() < confirmBar)
                return Optional.of(Chain);
        }
        return Optional.empty();
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

    public long getHighestSeenRank() {
        long maxRank = Long.MIN_VALUE;
        for (BlockmessChain<E,C,P> Chain : chains.values()) {
            long ChainMaxRank = Chain.getRankFromRefs(Chain.getBlockR());
            maxRank = Math.max(maxRank, ChainMaxRank);
        }
        return maxRank;
    }

    public List<BlockmessChain<E,C,P>> getAvailableChains() {
        return tryToGetAvailableChains();
    }

    @NotNull
    private List<BlockmessChain<E, C, P>> tryToGetAvailableChains() {
        List<BlockmessChain<E,C,P>> allChains = List.copyOf(chains.values());
        Set<UUID> preferable = getOrigin().getPriorityChains().stream()
                .map(BlockmessChain::getChainId).collect(toSet());
        return allChains.stream()
                .filter(b -> preferable.contains(b.getChainId()))
                .collect(toList());
    }

    @Override
    public List<StructuredValue<E>> generateContentListList(Collection<UUID> states, int usedSpace)
            throws IOException {
        return getOrigin().generateContentListList(states, usedSpace);
    }

    @Override
    public List<StructuredValue<E>> generateBoundContentListList(Collection<UUID> states, int usedSpace, int maxTxs)
            throws IOException {
        return getOrigin().generateBoundContentListList(states, usedSpace, maxTxs);
    }

    @Override
    public void submitContent(Collection<StructuredValue<E>> content) {
        getOrigin().submitContent(content);
    }

    @Override
    public void submitContent(StructuredValue<E> content) {
        getOrigin().submitContent(content);
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        getOrigin().deleteContent(contentIds);
    }

    @Override
    public Collection<StructuredValue<E>> getStoredContent() {
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
