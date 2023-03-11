package ledger.blockchain;


import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toList;

/**
 * This class is responsible for scheduling the blocks in the correct order. It is used by the {@link Blockchain} to
 * schedule the blocks that are received from the network.
 * <p> An incorrect order of blocks can happen when a block is received before its parent.</p>
 * <p> This is an unlikely scenario, but it can happen when a block is proposed shortly after its parent.</p>
 */
public class BlockScheduler {

	/**
	 * This map contains the blocks that are waiting for their parent to be received.
	 * The key is the parent identifier and the value is the list of blocks that are waiting for it.
	 */
	private final Map<UUID, List<ScheduledBlock>> blockDependencies = new ConcurrentHashMap<>();

	/**
	 * This method is called when an unordered block is received.
	 * It checks if the block is waiting for its parent.
	 * <p>If it is, adds the block to the list of blocks that are waiting for the parent.</p>
	 */
	public void submitUnorderedBlock(ScheduledBlock block, Collection<UUID> missingPrev) {
		for (UUID prev : missingPrev) {
			var prevDependencies = blockDependencies.computeIfAbsent(prev, k -> new ArrayList<>());
			prevDependencies.add(block);
		}
	}

	/**
	 * This method is called when an ordered block is received.
	 * <p>Clears the list of blocks that are waiting for the parent and returns the list of blocks that can be ordered.</p>
	 * <p>The list of blocks that can be ordered is the block itself and all the blocks that depend on it.</p>
	 * <p>The order of the blocks in the list follows a valid disposition of blocks to be delivered to the blockchain.</p>
	 */
	public List<ScheduledBlock> getValidOrdering(ScheduledBlock node) {
		List<ScheduledBlock> validOrder = new ArrayList<>();
		validOrder.add(node);
		validOrder.addAll(getDependents(node.getId()));
		return validOrder;
	}

	/**
	 * Recursive function that gets the blocks that depend on a given identifier.
	 * <p>The recursive call is not particularly expensive because the depth should never be big.</p>
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

	/**
	 * Checks if a block is ordered.
	 * <p>A block is ordered if it has no other block that depends on it.</p>
	 */
	private boolean isOrdered(ScheduledBlock node) {
		return node.getPrevious().stream().noneMatch(blockDependencies::containsKey);
	}

}
