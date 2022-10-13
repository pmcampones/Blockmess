package ledger.blockchain;


import main.GlobalProperties;

import java.util.*;
import java.util.stream.Collectors;

public class BlockScheduler {

	private static final long WAIT_DELAY_REORDER = 1000 * 5;

	private static final long VERIFICATION_INTERVAL = WAIT_DELAY_REORDER * 5;

	private final Map<UUID, List<BlockchainNode>> blockDependencies = new HashMap<>();

	public BlockScheduler() {
		Properties props = GlobalProperties.getProps();
	}

	public void submitUnorderedBlock(BlockchainNode block, Collection<UUID> missingPrev) {
		for (UUID prev : missingPrev) {
			var prevDependencies = blockDependencies.computeIfAbsent(prev, k -> new ArrayList<>());
			prevDependencies.add(block);
		}
	}

	public List<BlockchainNode> getValidOrdering(BlockchainNode node) {
		Set<BlockchainNode> dependents = getDependents(node.getBlockId());
		return getValidOrder(node, dependents);
	}

	private Set<BlockchainNode> getDependents(UUID id) {
		var idDependencies = blockDependencies.remove(id);
		if (idDependencies == null)
			idDependencies = Collections.emptyList();
		return idDependencies.stream().filter(this::isOrdered).collect(Collectors.toSet());
	}

	private boolean isOrdered(BlockchainNode node) {
		return node.getPrevious().stream().noneMatch(blockDependencies::containsKey);
	}

	private List<BlockchainNode> getValidOrder(BlockchainNode initialBlock, Set<BlockchainNode> blocks) {
		List<BlockchainNode> orderedBlocks = new ArrayList<>(List.of(initialBlock));
		Map<UUID, BlockchainNode> leftoverBlocks = blocks.stream()
				.collect(Collectors.toMap(BlockchainNode::getBlockId, x -> x));
		while (!leftoverBlocks.isEmpty()) {
			Set<UUID> allIds = leftoverBlocks.values().stream()
					.map(BlockchainNode::getBlockId).collect(Collectors.toSet());
			List<BlockchainNode> independentBlocks = leftoverBlocks.values().stream()
					.filter(node -> node.getPrevious().stream().noneMatch(allIds::contains))
					.collect(Collectors.toList());
			orderedBlocks.addAll(independentBlocks);
			independentBlocks.stream().map(BlockchainNode::getBlockId).forEach(leftoverBlocks::remove);
		}
		return orderedBlocks;
	}

}
