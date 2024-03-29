package sybilResistantElection;

import applicationInterface.BlockmessLauncher;
import applicationInterface.GlobalProperties;
import broadcastProtocols.BroadcastValue;
import cmux.AppOperation;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.BlockmessBlockImp;
import ledger.blocks.ContentList;
import ledger.ledgerManager.LedgerManager;
import ledger.ledgerManager.nodes.BlockmessChain;
import mempoolManager.MempoolManager;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sybilResistantElection.difficultyComputers.ConcurrentDifficultyComputer;
import sybilResistantElection.difficultyComputers.MultiChainDifficultyComputer;
import utils.CryptographicUtils;
import utils.IDGenerator;
import utils.merkleTree.ConcurrentMerkleTree;
import utils.merkleTree.ConsistentOrderMerkleTree;
import utils.merkleTree.MerkleTree;
import valueDispatcher.ValueDispatcher;

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

public class SybilResistantElection implements LedgerObserver {

	public static final short ID = IDGenerator.genId();
	public static final int INITIALIZATION_TIME = 120 * 1000;
	private static final Logger logger = LogManager.getLogger(SybilResistantElection.class);
	private final KeyPair self;

	private final LedgerManager blockmessRoot;
	private final MultiChainDifficultyComputer difficultyComputer;
	private final ReentrantLock lock = new ReentrantLock();
	private MerkleTree randomSeed;
	private LinkedHashMap<UUID, ChainSeed> chainSeeds = new LinkedHashMap<>();
	private int nonce = 0;

	public SybilResistantElection(KeyPair self) {
		this.self = self;
		this.blockmessRoot = LedgerManager.getSingleton();
		this.difficultyComputer = new ConcurrentDifficultyComputer(
				blockmessRoot.getAvailableChains().size());
		this.chainSeeds = replaceChainSeeds(blockmessRoot.getAvailableChains());
		this.randomSeed = computeRandomSeed();
		BroadcastValue.pojoSerializers.put(SybilResistantElectionProof.ID, SybilResistantElectionProof.serializer);
		MempoolManager.getSingleton().attachObserver(this);
		establishQueryTimer();
	}

	private MerkleTree computeRandomSeed() {
		List<byte[]> randomSeedElements = Stream.concat(
				Stream.of(self.getPublic().getEncoded()),
				chainSeeds.values().stream().map(ChainSeed::getChainSeed)
		).collect(toList());
		return new ConcurrentMerkleTree(new ConsistentOrderMerkleTree(randomSeedElements));
	}

	public void establishQueryTimer() {
		Properties props = GlobalProperties.getProps();
		int timeBetweenQueries = parseInt(props.getProperty("timeBetweenQueries", TIME_BETWEEN_QUERIES));
		int initializationTime = parseInt(props.getProperty("initializationTime",
				String.valueOf(INITIALIZATION_TIME)));
		long currTime = System.currentTimeMillis();
		long elapsed = currTime - BlockmessLauncher.startTime;
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
		BlockmessBlockImp block = new BlockmessBlockImp(1, List.of(placementChain.getPrevBlock()),
				placementChain.getCurrContent(), proof, self, placementChain.getChainId(),
				placementChain.getChain().getRankFromRefs(Set.of(placementChain.getPrevBlock())),
				blockmessRoot.getHighestSeenRank() + 1);
		ValueDispatcher.getSingleton().disseminateBlockRequest(block);
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

	private byte[] computeSolution() {
		nonce = (nonce + 1) % Integer.MAX_VALUE;
		ByteBuffer byteBuffer = ByteBuffer.wrap(randomSeed.getHashValue());
		byteBuffer.putInt(nonce);
		byte[] solution = CryptographicUtils.hashInput(byteBuffer.array());
		logger.debug("Solution with nonce {}, has {} leading zeros",
				nonce, difficultyComputer.getSolutionLeadingZeros(solution));
		return solution;
	}

	private LinkedHashMap<UUID, ChainSeed> replaceChainSeeds(
			List<BlockmessChain> updatedChains) {
		try {
			return tryToReplaceChainSeeds(updatedChains);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return chainSeeds;
	}

	private LinkedHashMap<UUID, ChainSeed> tryToReplaceChainSeeds(
			List<BlockmessChain> updatedChains) throws IOException {
		LinkedHashMap<UUID, ChainSeed> replacement = new LinkedHashMap<>(updatedChains.size());
		for (var chain : updatedChains) {
			ChainSeed oldSeed = chainSeeds.get(chain.getChainId());
			ChainSeed replacementSeed = oldSeed == null ?
					computeChainRandomSeed(chain) : oldSeed;
			replacement.put(replacementSeed.getChainId(), replacementSeed);
		}
		return replacement;
	}

	private ChainSeed computeChainRandomSeed(BlockmessChain chain)
			throws IOException {
		Set<UUID> prevBlocks = chain.getBlockR();
		List<AppOperation> contentLst = chain.generateOperationList(prevBlocks, getAproximateProofSize());
		ContentList content = new ContentList(contentLst);
		return new ChainSeed(chain.getChainId(), prevBlocks.iterator().next(), content, chain);
	}

	private int getAproximateProofSize() {
		return Integer.BYTES + chainSeeds.size() * (2 * Long.BYTES + 32 * Byte.BYTES);
	}

	@Override
	public void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
		try {
			lock.lock();
			updateMetaContentList(block);
		} finally {
			lock.unlock();
		}
	}

	private void updateMetaContentList(BlockmessBlock block) {
		List<BlockmessChain> chains = blockmessRoot.getAvailableChains();
		if (wereChainsChanged(chains))
			reactToChangeInNumberOfChains(chains);
		replaceChainIfNecessary(block);
	}

	private void replaceChainIfNecessary(BlockmessBlock newBlock) {
		try {
			tryToReplaceChainIfNecessary(newBlock);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void tryToReplaceChainIfNecessary(BlockmessBlock newBlock)
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
		BlockmessChain chain = oldSeed.getChain();
		List<AppOperation> contentLst = chain.generateOperationList(newPrevs, getAproximateProofSize());
		ContentList newContent = new ContentList(contentLst);
		ChainSeed newChainSeed = new ChainSeed(oldSeed.getChainId(), newPrev, newContent, oldSeed.getChain());
		chainSeeds.replace(oldSeed.getChainId(), newChainSeed);
		randomSeed.replaceLeaf(oldSeed.getChainSeed(), newChainSeed.getChainSeed());
	}

	private void reactToChangeInNumberOfChains(List<BlockmessChain> chains) {
		difficultyComputer.setNumChains(chains.size());
		chainSeeds = replaceChainSeeds(chains);
		randomSeed = computeRandomSeed();
		logger.info("There are currently {} active chains, resulting in valid proofs of {}  leading zeros",
				chainSeeds.size(), difficultyComputer.getNumLeadingZeros());
	}

	private boolean wereChainsChanged(List<BlockmessChain> updatedChains) {
		return !(updatedChains.size() == chainSeeds.size()
				&& updatedChains.stream()
				.map(BlockmessChain::getChainId)
				.allMatch(chainSeeds::containsKey));
	}

	@Override
	public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
		List<BlockmessChain> chains = blockmessRoot.getAvailableChains();
		try {
			lock.lock();
			if (wereChainsChanged(chains))
				reactToChangeInNumberOfChains(chains);
		} finally {
			lock.unlock();
		}
	}
}
