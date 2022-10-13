package ledger.blockchain;


import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.util.stream.Collectors.toList;

public class BlockScheduler {

	private final Map<UUID, List<BlockchainNode>> blockDependencies = new HashMap<>();

	public void submitUnorderedBlock(BlockchainNode block, Collection<UUID> missingPrev) {
		for (UUID prev : missingPrev) {
			var prevDependencies = blockDependencies.computeIfAbsent(prev, k -> new ArrayList<>());
			prevDependencies.add(block);
		}
	}

	public List<BlockchainNode> getValidOrdering(BlockchainNode node) {
		List<BlockchainNode> validOrder = new ArrayList<>();
		validOrder.add(node);
		validOrder.addAll(getDependents(node.getBlockId()));
		return validOrder;
	}

	/**
	 * Recursive function that gets the blocks that depend on a given identifier. The recursive call is not particularly
	 * expensive because the depth should never be big.
	 */
	private List<BlockchainNode> getDependents(UUID id) {
		var idDependencies = blockDependencies.remove(id);
		if (idDependencies == null)
			idDependencies = Collections.emptyList();
		List<BlockchainNode> dependents = getDirectDependents(idDependencies);
		List<BlockchainNode> indirectDependents = dependents.stream()
				.map(BlockchainNode::getBlockId)
				.map(this::getDependents)
				.flatMap(Collection::stream).collect(toList());
		dependents.addAll(indirectDependents);
		return dependents;
	}

	@NotNull
	private List<BlockchainNode> getDirectDependents(List<BlockchainNode> idDependencies) {
		return idDependencies.stream().filter(this::isOrdered).collect(toList());
	}

	private boolean isOrdered(BlockchainNode node) {
		return node.getPrevious().stream().noneMatch(blockDependencies::containsKey);
	}

}
