package sybilResistantElection;

import catecoin.blocks.ContentList;
import catecoin.notifications.DeliverFinalizedBlockIdentifiersNotification;
import catecoin.txs.Transaction;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.BlockmessBlockImp;
import ledger.ledgerManager.LedgerManager;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.nodes.BlockmessChain;
import ledger.notifications.DeliverNonFinalizedBlockNotification;
import main.Main;
import main.ProtoPojo;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantElection.difficultyComputers.ConcurrentDifficultyComputer;
import sybilResistantElection.difficultyComputers.MultiChainDifficultyComputer;
import utils.CryptographicUtils;
import utils.IDGenerator;
import utils.merkleTree.ConcurrentMerkleTree;
import utils.merkleTree.ConsistentOrderMerkleTree;
import utils.merkleTree.MerkleTree;
import valueDispatcher.ValueDispatcher;
import valueDispatcher.requests.DisseminateSignedBlockRequest;

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
import static sybilResistantElection.difficultyComputers.BaseDifficultyComputer.TIME_BETWEEN_QUERIES;

public class SybilResistantElection extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(SybilResistantElection.class);

    public static final short ID = IDGenerator.genId();

    public static final int INITIALIZATION_TIME = 120 * 1000;

    private final KeyPair self;

    private final LedgerManager blockmessRoot;

    private MerkleTree randomSeed;
    private LinkedHashMap<UUID, ChainSeed> chainSeeds = new LinkedHashMap<>();

    private final MultiChainDifficultyComputer difficultyComputer;

    private final ReentrantLock lock = new ReentrantLock();

    private int nonce = 0;

    public SybilResistantElection(Properties props, KeyPair self, LedgerManager blockmessRoot) throws HandlerRegistrationException {
        super(SybilResistantElection.class.getSimpleName(), ID);
        this.self = self;
        this.blockmessRoot = blockmessRoot;
        this.difficultyComputer = new ConcurrentDifficultyComputer(props,
                blockmessRoot.getAvailableChains().size());
        this.chainSeeds = replaceChainSeeds(blockmessRoot.getAvailableChains());
        this.randomSeed = computeRandomSeed();
        ProtoPojo.pojoSerializers.put(SybilResistantElectionProof.ID, SybilResistantElectionProof.serializer);
        subscribeNotifications();
    }

    private MerkleTree computeRandomSeed() {
        List<byte[]> randomSeedElements = Stream.concat(
                Stream.of(self.getPublic().getEncoded()),
                chainSeeds.values().stream().map(ChainSeed::getChainSeed)
        ).collect(toList());
        return new ConcurrentMerkleTree(new ConsistentOrderMerkleTree(randomSeedElements));
    }

    private void subscribeNotifications() throws HandlerRegistrationException {
        subscribeNotification(DeliverNonFinalizedBlockNotification.ID,
                (DeliverNonFinalizedBlockNotification<BlockmessBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof>> notif1, short source1) -> uponDeliverNonFinalizedBlockNotification(notif1));
        subscribeNotification(DeliverFinalizedBlockIdentifiersNotification.ID,
                (DeliverFinalizedBlockIdentifiersNotification notif, short source) -> uponDeliverFinalizedBlockNotification());
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        int timeBetweenQueries = parseInt(props.getProperty("timeBetweenQueries", TIME_BETWEEN_QUERIES));
        int initializationTime = parseInt(props.getProperty("initializationTime",
                String.valueOf(INITIALIZATION_TIME)));
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
        ChainSeed placementChain = multiplexChain(solution);
        List<Pair<UUID, byte[]>> chainSeeds = computeChainSeedsList();
        SybilResistantElectionProof proof = new SybilResistantElectionProof(chainSeeds, nonce);
        BlockmessBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof> block =
                new BlockmessBlockImp<>(1, List.of(placementChain.getPrevBlock()),
                        placementChain.getCurrContent(), proof, self, placementChain.getChainId(),
                        placementChain.getChain().getRankFromRefs(Set.of(placementChain.getPrevBlock())),
                        blockmessRoot.getHighestSeenRank() + 1);
        sendRequest(new DisseminateSignedBlockRequest<>(block), ValueDispatcher.ID);
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

    private ChainSeed multiplexChain(byte[] solution) {
        long lastInteger = Integer.toUnsignedLong(getLastInteger(solution));
        int numChains = chainSeeds.size();
        long maxUnsignedInteger = 1L << Integer.SIZE;
        long ChainInterval = maxUnsignedInteger / numChains;
        long accum = 0;
        ChainSeed currProof;
        Iterator<ChainSeed> it = chainSeeds.values().iterator();
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
            DeliverNonFinalizedBlockNotification<BlockmessBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof>> notif) {
        try {
            lock.lock();
            updateMetaContentList(notif);
        } finally {
            lock.unlock();
        }
    }

    private void updateMetaContentList(DeliverNonFinalizedBlockNotification<BlockmessBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof>> notif) {
        try {
            Thread.sleep(100);  //Enough time for the mempool manager to process the block.
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        List<BlockmessChain<Transaction, SybilResistantElectionProof>> chains = blockmessRoot.getAvailableChains();
        if (wereChainsChanged(chains))
            reactToChangeInNumberOfChains(chains);
        BlockmessBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof> updatedChain = notif.getNonFinalizedBlock();
        replaceChainIfNecessary(updatedChain);
    }

    private void reactToChangeInNumberOfChains(List<BlockmessChain<Transaction, SybilResistantElectionProof>> chains) {
        difficultyComputer.setNumChains(chains.size());
        chainSeeds = replaceChainSeeds(chains);
        randomSeed = computeRandomSeed();
        logger.info("There are currently {} active chains, resulting in valid proofs of {}  leading zeros",
                chainSeeds.size(), difficultyComputer.getNumLeadingZeros());
    }

    private LinkedHashMap<UUID, ChainSeed> replaceChainSeeds(
            List<BlockmessChain<Transaction, SybilResistantElectionProof>> updatedChains) {
        try {
            return tryToReplaceChainSeeds(updatedChains);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return chainSeeds;
    }

    private LinkedHashMap<UUID, ChainSeed> tryToReplaceChainSeeds(
            List<BlockmessChain<Transaction, SybilResistantElectionProof>> updatedChains) throws IOException {
        LinkedHashMap<UUID, ChainSeed> replacement = new LinkedHashMap<>(updatedChains.size());
        for (var chain : updatedChains) {
            ChainSeed oldSeed = chainSeeds.get(chain.getChainId());
            ChainSeed replacementSeed = oldSeed == null ?
                    computeChainRandomSeed(chain) : oldSeed;
            replacement.put(replacementSeed.getChainId(), replacementSeed);
        }
        return replacement;
    }

    private ChainSeed computeChainRandomSeed(BlockmessChain<Transaction, SybilResistantElectionProof> chain)
            throws IOException {
        Set<UUID> prevBlocks = chain.getBlockR();
        List<StructuredValue<Transaction>> contentLst = chain.generateContentListList(prevBlocks, getAproximateProofSize());
        ContentList<StructuredValue<Transaction>> content = new ContentList<>(contentLst);
        return new ChainSeed(chain.getChainId(), prevBlocks.iterator().next(), content, chain);
    }

    private boolean wereChainsChanged(List<BlockmessChain<Transaction, SybilResistantElectionProof>> updatedChains) {
        return !(updatedChains.size() == chainSeeds.size()
                && updatedChains.stream()
                .map(BlockmessChain::getChainId)
                .allMatch(chainSeeds::containsKey));
    }

    private void uponDeliverFinalizedBlockNotification() {
        List<BlockmessChain<Transaction, SybilResistantElectionProof>> chains = blockmessRoot.getAvailableChains();
        try {
            lock.lock();
            if (wereChainsChanged(chains))
                reactToChangeInNumberOfChains(chains);
        } finally {
            lock.unlock();
        }
    }

    private void replaceChainIfNecessary(BlockmessBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof> newBlock) {
        try {
            tryToReplaceChainIfNecessary(newBlock);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void tryToReplaceChainIfNecessary(BlockmessBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof> newBlock)
            throws IOException {
        ChainSeed oldSeed = chainSeeds.get(newBlock.getDestinationChain());
        if (oldSeed != null) {
            Set<UUID> newPrevs = oldSeed.getChain().getBlockR();
            if (!newPrevs.contains(oldSeed.getPrevBlock()))
                replaceChain(oldSeed, newPrevs);
        }
    }

    private void replaceChain(ChainSeed oldSeed, Set<UUID> newPrevs) throws IOException {
        UUID newPrev = newPrevs.iterator().next();
        BlockmessChain<Transaction, SybilResistantElectionProof> chain = oldSeed.getChain();
        List<StructuredValue<Transaction>> contentLst = chain.generateContentListList(newPrevs, getAproximateProofSize());
        ContentList<StructuredValue<Transaction>> newContent = new ContentList<>(contentLst);
        ChainSeed newChainSeed =
                new ChainSeed(oldSeed.getChainId(), newPrev, newContent, oldSeed.getChain());
        chainSeeds.replace(oldSeed.getChainId(), newChainSeed);
        randomSeed.replaceLeaf(oldSeed.getChainSeed(), newChainSeed.getChainSeed());
    }

    private int getAproximateProofSize() {
        return Integer.BYTES + chainSeeds.size() * (2 * Long.BYTES + 32 * Byte.BYTES);
    }

}
