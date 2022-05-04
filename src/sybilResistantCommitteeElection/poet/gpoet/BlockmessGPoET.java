package sybilResistantCommitteeElection.poet.gpoet;

import catecoin.blocks.SimpleBlockContentList;
import catecoin.notifications.DeliverFinalizedBlockIdentifiersNotification;
import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.BlockmessBlockImp;
import ledger.ledgerManager.BlockmessRoot;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.nodes.BlockmessChain;
import ledger.notifications.DeliverNonFinalizedBlockNotification;
import main.CryptographicUtils;
import main.Main;
import main.ProtoPojo;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.notifications.IWasElectedWithBlockNotification;
import sybilResistantCommitteeElection.poet.GPoETChainSeed;
import sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers.BlockmessGPoETDifficultyComputer;
import sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers.ConcurrentBlockmessGPoETDifficultyComputer;
import utils.IDGenerator;
import utils.merkleTree.ConcurrentMerkleTree;
import utils.merkleTree.ConsistentOrderMerkleTree;
import utils.merkleTree.MerkleTree;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.toList;
import static sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers.LedgerGPoETDifficultyComputer.TIME_BETWEEN_QUERIES;

public class BlockmessGPoET<E extends IndexableContent, C extends BlockContent<StructuredValue<E>>>
        extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(BlockmessGPoET.class);

    public static final short ID = IDGenerator.genId();

    public static final int INITIALIZATION_TIME = 120 * 1000;

    private final int timeBetweenQueries;

    private final int initializationTime;

    private final KeyPair self;

    private LinkedHashMap<UUID, GPoETChainSeed<E,C>> chainSeeds = new LinkedHashMap<>();

    private MerkleTree randomSeed;

    private final BlockmessRoot<E,C,BlockmessGPoETProof> blockmessRoot;

    private final BlockmessGPoETDifficultyComputer difficultyComputer;

    private final ReentrantLock lock = new ReentrantLock();

    private int nonce = 0;

    public BlockmessGPoET(Properties props, KeyPair self, BlockmessRoot<E, C, BlockmessGPoETProof> blockmessRoot) throws HandlerRegistrationException {
        super(BlockmessGPoET.class.getSimpleName(), ID);
        this.self = self;
        this.blockmessRoot = blockmessRoot;
        this.timeBetweenQueries = parseInt(props.getProperty("timeBetweenQueries", TIME_BETWEEN_QUERIES));
        this.initializationTime = parseInt(props.getProperty("initializationTime",
                String.valueOf(INITIALIZATION_TIME)));
        this.difficultyComputer = new ConcurrentBlockmessGPoETDifficultyComputer(props,
                blockmessRoot.getAvailableChains().size());
        this.chainSeeds = replaceChainSeeds(blockmessRoot.getAvailableChains());
        this.randomSeed = computeRandomSeed();
        ProtoPojo.pojoSerializers.put(BlockmessGPoETProof.ID, BlockmessGPoETProof.serializer);
        subscribeNotifications();
    }

    private MerkleTree computeRandomSeed() {
        List<byte[]> randomSeedElements = Stream.concat(
                Stream.of(self.getPublic().getEncoded()),
                chainSeeds.values().stream().map(GPoETChainSeed::getChainSeed)
        ).collect(toList());
        return new ConcurrentMerkleTree(new ConsistentOrderMerkleTree(randomSeedElements));
    }

    private void subscribeNotifications() throws HandlerRegistrationException {
        subscribeNotification(DeliverNonFinalizedBlockNotification.ID,
                (DeliverNonFinalizedBlockNotification<BlockmessBlock<C, BlockmessGPoETProof>> notif1, short source1) -> uponDeliverNonFinalizedBlockNotification(notif1));
        subscribeNotification(DeliverFinalizedBlockIdentifiersNotification.ID,
                (DeliverFinalizedBlockIdentifiersNotification notif, short source) -> uponDeliverFinalizedBlockNotification());
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {
        long currTime = System.currentTimeMillis();
        long elapsed = currTime - Main.startTime;
        long remainder = initializationTime - elapsed;
        logger.info("Starting to generate Sybil Resistant Proof for block in {} miliseconds.",
                remainder);
        Executors.newScheduledThreadPool(1)
                .scheduleAtFixedRate(new Thread(this::attemptToProposeBlock), remainder, timeBetweenQueries, TimeUnit.MILLISECONDS);
    }

    private void attemptToProposeBlock() {
        byte[] solution = computeSolution();
        if (difficultyComputer.hasEnoughLeadingZeros(solution))
            proposeBlock(solution);
    }

    private void proposeBlock(byte[] solution) {
        try {
            tryToProposeBlock(solution);
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void tryToProposeBlock(byte[] solution) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, IOException {
        logger.info("Found valid solution with nonce {}, with {} leading zeros",
                nonce, difficultyComputer.getSolutionLeadingZeros(solution));
        GPoETChainSeed<E,C> placementChain = multiplexChain(solution);
        List<Pair<UUID, byte[]>> chainSeeds = computeChainSeedsList();
        BlockmessGPoETProof proof = new BlockmessGPoETProof(chainSeeds, nonce);
        BlockmessBlock<C, BlockmessGPoETProof> block =
                new BlockmessBlockImp<>(1, List.of(placementChain.getPrevBlock()),
                        placementChain.getCurrContent(), proof, self, placementChain.getChainId(),
                        placementChain.getChain().getRankFromRefs(Set.of(placementChain.getPrevBlock())),
                        blockmessRoot.getHighestSeenRank() + 1);
        triggerNotification(new IWasElectedWithBlockNotification<>(block));
    }

    private byte[] computeSolution() {
        nonce = (nonce + 1) % Integer.MAX_VALUE;
        ByteBuffer byteBuffer = ByteBuffer.wrap(randomSeed.getHashValue());
        byteBuffer.putInt(nonce);
        byte[] solution = CryptographicUtils.hashInput(byteBuffer.array());
        logger.debug("Solution with nonce {}, has {} leading zeros",
                nonce, difficultyComputer.getSolutionLeadingZeros(solution));
        return solution;
    }

    private List<Pair<UUID, byte[]>> computeChainSeedsList() {
        return chainSeeds.values().stream()
                .map(b -> Pair.of(b.getChainId(), b.getChainSeed()))
                .collect(toList());
    }

    private GPoETChainSeed<E,C> multiplexChain(byte[] solution) {
        long lastInteger = Integer.toUnsignedLong(getLastInteger(solution));
        int numChains = chainSeeds.size();
        long maxUnsignedInteger = 1L << Integer.SIZE;
        long ChainInterval = maxUnsignedInteger / numChains;
        long accum = 0;
        GPoETChainSeed<E,C> currProof;
        Iterator<GPoETChainSeed<E,C>> it = chainSeeds.values().iterator();
        do {
            currProof = it.next();
            accum += ChainInterval;
        } while (lastInteger > accum);
        return currProof;
    }

    private int getLastInteger(byte[] array) {
        byte[] intArray = new byte[Integer.BYTES];
        for (int i = Integer.BYTES; i > 0; i--)
            intArray[Integer.BYTES - i] = array[array.length - i];
        ByteBuffer byteBuffer = ByteBuffer.wrap(intArray);
        return byteBuffer.getInt();
    }

    private void uponDeliverNonFinalizedBlockNotification(
            DeliverNonFinalizedBlockNotification<BlockmessBlock<C, BlockmessGPoETProof>> notif) {
        try {
            lock.lock();
            updateMetablockContent(notif);
        } finally {
            lock.unlock();
        }
    }

    private void updateMetablockContent(DeliverNonFinalizedBlockNotification<BlockmessBlock<C, BlockmessGPoETProof>> notif) {
        try {
            Thread.sleep(100);  //Enough time for the mempool manager to process the block.
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        List<BlockmessChain<E,C,BlockmessGPoETProof>> chains = blockmessRoot.getAvailableChains();
        if (wereChainsChanged(chains))
            reactToChangeInNumberOfChains(chains);
        BlockmessBlock<C,BlockmessGPoETProof> updatedChain = notif.getNonFinalizedBlock();
        replaceChainIfNecessary(updatedChain);
    }

    private void uponDeliverFinalizedBlockNotification() {
        List<BlockmessChain<E,C,BlockmessGPoETProof>> chains = blockmessRoot.getAvailableChains();
        try {
            lock.lock();
            if (wereChainsChanged(chains))
                reactToChangeInNumberOfChains(chains);
        } finally {
            lock.unlock();
        }
    }

    private void reactToChangeInNumberOfChains(List<BlockmessChain<E, C, BlockmessGPoETProof>> chains) {
        difficultyComputer.setNumChains(chains.size());
        chainSeeds = replaceChainSeeds(chains);
        randomSeed = computeRandomSeed();
        logger.info("There are currently {} active chains, resulting in valid proofs of {}  leading zeros",
                chainSeeds.size(), difficultyComputer.getNumLeadingZeros());
    }

    private boolean wereChainsChanged(List<BlockmessChain<E,C,BlockmessGPoETProof>> updatedChains) {
        return !(updatedChains.size() == chainSeeds.size()
                && updatedChains.stream()
                .map(BlockmessChain::getChainId)
                .allMatch(chainSeeds::containsKey));
    }

    private LinkedHashMap<UUID, GPoETChainSeed<E,C>> replaceChainSeeds(
            List<BlockmessChain<E,C,BlockmessGPoETProof>> updatedChains) {
        try {
            return tryToReplaceChainSeeds(updatedChains);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return chainSeeds;
    }

    private LinkedHashMap<UUID, GPoETChainSeed<E,C>> tryToReplaceChainSeeds(
            List<BlockmessChain<E,C,BlockmessGPoETProof>> updatedChains) throws IOException {
        LinkedHashMap<UUID, GPoETChainSeed<E,C>> replacement = new LinkedHashMap<>(updatedChains.size());
        for (var chain : updatedChains) {
            GPoETChainSeed<E,C> oldSeed = chainSeeds.get(chain.getChainId());
            GPoETChainSeed<E,C> replacementSeed = oldSeed == null ?
                    computeChainRandomSeed(chain) : oldSeed;
            replacement.put(replacementSeed.getChainId(), replacementSeed);
        }
        return replacement;
    }

    private GPoETChainSeed<E,C> computeChainRandomSeed(BlockmessChain<E,C,BlockmessGPoETProof> chain)
            throws IOException {
        Set<UUID> prevBlocks = chain.getBlockR();
        List<StructuredValue<E>> contentLst = chain.generateBlockContentList(prevBlocks, getAproximateProofSize());
        C content = (C) new SimpleBlockContentList<>(contentLst);
        return new GPoETChainSeed<>(chain.getChainId(), prevBlocks.iterator().next(), content, chain);
    }

    private void replaceChainIfNecessary(BlockmessBlock<C, BlockmessGPoETProof> newBlock) {
        try {
            tryToReplaceChainIfNecessary(newBlock);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void tryToReplaceChainIfNecessary(BlockmessBlock<C, BlockmessGPoETProof> newBlock)
            throws IOException {
        GPoETChainSeed<E,C> oldSeed = chainSeeds.get(newBlock.getDestinationChain());
        if (oldSeed != null) {
            Set<UUID> newPrevs = oldSeed.getChain().getBlockR();
            if (!newPrevs.contains(oldSeed.getPrevBlock()))
                replaceChain(oldSeed, newPrevs);
        }
    }

    private void replaceChain(GPoETChainSeed<E, C> oldSeed, Set<UUID> newPrevs) throws IOException {
        UUID newPrev = newPrevs.iterator().next();
        BlockmessChain<E,C,BlockmessGPoETProof> chain = oldSeed.getChain();
        List<StructuredValue<E>> contentLst = chain.generateBlockContentList(newPrevs, getAproximateProofSize());
        C newContent = (C) new SimpleBlockContentList<>(contentLst);
        GPoETChainSeed<E,C> newChainSeed =
                new GPoETChainSeed<>(oldSeed.getChainId(), newPrev, newContent, oldSeed.getChain());
        chainSeeds.replace(oldSeed.getChainId(), newChainSeed);
        randomSeed.replaceLeaf(oldSeed.getChainSeed(), newChainSeed.getChainSeed());
    }

    private int getAproximateProofSize() {
        return Integer.BYTES + chainSeeds.size() * (2 * Long.BYTES + 32 * Byte.BYTES);
    }

}
