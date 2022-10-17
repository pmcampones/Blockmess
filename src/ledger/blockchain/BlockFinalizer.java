package ledger.blockchain;

import cyclops.control.Trampoline;
import lombok.Getter;
import main.GlobalProperties;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static cyclops.control.Trampoline.more;
import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.*;

public class BlockFinalizer {

	public static final int FINALIZED_WEIGHT = 6;
	private static final Logger logger = LogManager.getLogger(Blockchain.class.getName());


	public final Map<UUID, BlockchainNode> blocks = new HashMap<>();

	//Only used in the unit tests
	public final Map<UUID, BlockchainNode> finalized = new HashMap<>();

	@Getter
	public final int finalizedWeight;
	//Collection containing the blocks that have no other block referencing them.
	//Effectively being the tips of their forks.
	private final Map<UUID, BlockchainNode> chainTips = new HashMap<>();
	private BlockchainNode priorityTip;
	private BlockchainNode lastFinalized;

	public BlockFinalizer(UUID genesisId) {
		Properties props = GlobalProperties.getProps();
		this.finalizedWeight = parseInt(props.getProperty("finalizedWeight",
				String.valueOf(FINALIZED_WEIGHT)));
		createGenesisBlock(genesisId);
	}

	private void createGenesisBlock(UUID genesisUUID) {
		BlockchainNode genesis = new BlockchainNode(genesisUUID,
				Collections.emptySet(), finalizedWeight);
		chainTips.put(genesis.getId(), genesis);
		blocks.put(genesis.getId(), genesis);
		finalized.put(genesis.getId(), genesis);
		lastFinalized = genesis;
		priorityTip = genesis;
	}

	public Pair<List<UUID>, Set<UUID>> addBlock(UUID id, Set<UUID> prevIds, int weight) {
		BlockchainNode prev = blocks.get(prevIds.iterator().next());
		int chainWeight = weight + prev.getWeight();
		BlockchainNode block = new BlockchainNode(id, prevIds, chainWeight);
		prev.getFollowing().add(id);
		chainTips.remove(prev.getId());
		chainTips.put(id, block);
		if (block.getWeight() > priorityTip.getWeight())
			priorityTip = block;
		logger.debug("Inserting: {}", block.getId());
		blocks.put(block.getId(), block);
		Set<UUID> deleted = deleteForkedChains();
		List<BlockchainNode> finalizedSequence = finalizeForward();
		finalized.putAll(finalizedSequence.stream().collect(toMap(BlockchainNode::getId, b -> b)));
		List<UUID> finalizedIds = finalizedSequence.stream().map(BlockchainNode::getId).collect(toList());
		return Pair.of(finalizedIds, deleted);
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
				logger.debug("Finalizing: {}", lastFinalized.getId());
				newlyFinalized.add(lastFinalized);
			} while (lastFinalized.getFollowing().size() < 2 && maxWeight - lastFinalized.getWeight() > finalizedWeight);

		return newlyFinalized;
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
		chainTips.remove(chainTip.getId());
		BlockchainNode currentBlock = chainTip;
		BlockchainNode previousBlock = null;
		while (currentBlock.getFollowing().size() < 2) {
			blocks.remove(currentBlock.getId());
			deleted.add(currentBlock.getId());
			logger.debug("Removing: {}", currentBlock.getId());
			previousBlock = currentBlock;
			currentBlock = blocks.get(currentBlock.getPrevious().iterator().next());
		}
		assert previousBlock != null;
		currentBlock.getFollowing().remove(previousBlock.getId());
		return deleted;
	}

	public boolean isInLongestChain(UUID nodeId) {
		BlockchainNode node = blocks.get(nodeId);
		if (node == null)
			return false;
		if (chainTips.containsKey(nodeId))
			return node.getWeight() == getMaxWeight();
		return node.getFollowing().stream().anyMatch(this::isInLongestChain);
	}

	private int getMaxWeight() {
		return chainTips.values().stream()
				.mapToInt(BlockchainNode::getWeight)
				.max().orElse(0);
	}

	Set<UUID> getForkBlocks(Set<UUID> curr, int depth) {
		return recursiveGetForkBlocks(curr, depth).result();
	}

	private Trampoline<Set<UUID>> recursiveGetForkBlocks(Set<UUID> curr, int depth) {
		if (depth == 0)
			return Trampoline.done(curr);
		Set<UUID> prev = curr.stream()
				.map(blocks::get)
				.map(BlockchainNode::getPrevious)
				.flatMap(Collection::stream)
				.collect(toSet());
		return more(() -> recursiveGetForkBlocks(prev, depth - 1));
	}

	//DO NOT PLACE LOCK HERE: DEADLOCK WILL ENSUE
	//THERE IS A CYCLIC DEPENDENCY BETWEEN THIS CLASS AND THE DELAYVERIFIER (sorry)
	boolean hasBlock(UUID blockId) {
		return blocks.containsKey(blockId);
	}

	public Set<UUID> getBlockR() {
		assert !chainTips.isEmpty();
		return Set.of(priorityTip.getId());
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

	public int getWeight(UUID block) throws IllegalArgumentException {
		BlockchainNode node = blocks.get(block);
		if (node == null) throw new IllegalArgumentException();
		return node.getWeight();
	}

	public Set<UUID> getNodesIds() {
		return blocks.keySet();
	}

	public Set<UUID> getFinalizedIds() {
		return finalized.keySet();
	}

}
