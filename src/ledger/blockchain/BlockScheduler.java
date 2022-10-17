package ledger.blockchain;


import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toList;

public class BlockScheduler {

	private final Map<UUID, List<ScheduledBlock>> blockDependencies = new ConcurrentHashMap<>();

	public void submitUnorderedBlock(ScheduledBlock block, Collection<UUID> missingPrev) {
		for (UUID prev : missingPrev) {
			var prevDependencies = blockDependencies.computeIfAbsent(prev, k -> new ArrayList<>());
			prevDependencies.add(block);
		}
	}

	public List<ScheduledBlock> getValidOrdering(ScheduledBlock node) {
		List<ScheduledBlock> validOrder = new ArrayList<>();
		validOrder.add(node);
		validOrder.addAll(getDependents(node.getId()));
		return validOrder;
	}

	/**
	 * Recursive function that gets the blocks that depend on a given identifier. The recursive call is not particularly
	 * expensive because the depth should never be big.
	 */
	private List<ScheduledBlock> getDependents(UUID id) {
		var idDependencies = blockDependencies.remove(id);
		if (idDependencies == null)
			idDependencies = Collections.emptyList();
		List<ScheduledBlock> dependents = getDirectDependents(idDependencies);
		List<ScheduledBlock> indirectDependents = dependents.stream()
				.map(ScheduledBlock::getId)
				.map(this::getDependents)
				.flatMap(Collection::stream).collect(toList());
		dependents.addAll(indirectDependents);
		return dependents;
	}

	@NotNull
	private List<ScheduledBlock> getDirectDependents(List<ScheduledBlock> idDependencies) {
		return idDependencies.stream().filter(this::isOrdered).collect(toList());
	}

	private boolean isOrdered(ScheduledBlock node) {
		return node.getPrevious().stream().noneMatch(blockDependencies::containsKey);
	}

}
