import ledger.blockchain.BlockScheduler;
import ledger.blockchain.BlockchainNode;
import main.BlockmessLauncher;
import main.GlobalProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

public class BlockSchedulerTests {

	private static boolean isInitialized = false;

	private final BlockScheduler blockScheduler;

	public BlockSchedulerTests() throws Exception {
		if (!isInitialized) {
			Properties props = Babel.loadConfig(new String[]{}, BlockmessLauncher.DEFAULT_CONF);
			GlobalProperties.setProps(props);
			isInitialized = true;
		}
		blockScheduler = new BlockScheduler();
	}

	@Test
	public void shouldReturnSingleBlock() {
		BlockchainNode node = genNoDependencies();
		var ordering = blockScheduler.getValidOrdering(node);
		assertEquals(1, ordering.size());
		assertEquals(node.getBlockId(), ordering.get(0).getBlockId());
	}

	@NotNull
	private BlockchainNode genNoDependencies() {
		return genBlock(Collections.emptySet());
	}

	private BlockchainNode genBlock(Set<UUID> prev) {
		return new BlockchainNode(UUID.randomUUID(), prev, 1);
	}

	@Test
	public void shouldReturnUnorderedAfterSingleDependencyIsResolved() {
		BlockchainNode genesis = genNoDependencies();
		BlockchainNode unordered = genBlock(Set.of(genesis.getBlockId()));
		blockScheduler.submitUnorderedBlock(unordered, Set.of(genesis.getBlockId()));
		var ordering = blockScheduler.getValidOrdering(genesis);
		assertEquals(2, ordering.size());
		assertEquals(genesis.getBlockId(), ordering.get(0).getBlockId());
		assertEquals(unordered.getBlockId(), ordering.get(1).getBlockId());
	}

	@Test
	public void shouldNotReturnUnorderedUntilSingleDependencyIsResolved() {
		BlockchainNode ordered1 = genNoDependencies();
		BlockchainNode unordered1 = genBlock(Set.of(ordered1.getBlockId()));
		BlockchainNode ordered2 = genNoDependencies();
		BlockchainNode unordered2 = genBlock(Set.of(ordered2.getBlockId()));
		blockScheduler.submitUnorderedBlock(unordered1, Set.of(ordered1.getBlockId()));
		blockScheduler.submitUnorderedBlock(unordered2, Set.of(ordered2.getBlockId()));
		var ordering = blockScheduler.getValidOrdering(ordered1);
		List<UUID> orderingIds = getOrderingIds(ordering);
		assertEquals(2, ordering.size());
		assertFalse(orderingIds.contains(unordered2.getBlockId()));
	}

	@NotNull
	private static List<UUID> getOrderingIds(List<BlockchainNode> ordering) {
		return ordering.stream().map(BlockchainNode::getBlockId).collect(toList());
	}

	@Test
	public void shouldReturnUnorderedAfterAllDependenciesAreResolved() {
		BlockchainNode dependency1 = genNoDependencies();
		BlockchainNode dependency2 = genNoDependencies();
		BlockchainNode unordered = genBlock(Set.of(dependency1.getBlockId(), dependency2.getBlockId()));
		blockScheduler.submitUnorderedBlock(unordered, Set.of(dependency1.getBlockId(), dependency2.getBlockId()));
		blockScheduler.getValidOrdering(dependency1);
		var ordering = blockScheduler.getValidOrdering(dependency2);
		List<UUID> orderingIds = getOrderingIds(ordering);
		assertTrue(orderingIds.contains(unordered.getBlockId()));
	}

	@Test
	public void shouldNotReturnUnorderedUntilAllDependenciesAreResolved() {
		BlockchainNode dependency1 = genNoDependencies();
		BlockchainNode dependency2 = genNoDependencies();
		BlockchainNode unordered = genBlock(Set.of(dependency1.getBlockId(), dependency2.getBlockId()));
		blockScheduler.submitUnorderedBlock(unordered, Set.of(dependency1.getBlockId(), dependency2.getBlockId()));
		var ordering = blockScheduler.getValidOrdering(dependency1);
		List<UUID> orderingIds = getOrderingIds(ordering);
		assertEquals(1, ordering.size());
		assertFalse(orderingIds.contains(unordered.getBlockId()));
	}

	@Test
	public void shouldReturnWhenDependingOnResolvedUnorderedBlock() {
		BlockchainNode genesis = genNoDependencies();
		BlockchainNode middle = genBlock(Set.of(genesis.getBlockId()));
		BlockchainNode last = genBlock(Set.of(middle.getBlockId()));
		blockScheduler.submitUnorderedBlock(last, Set.of(middle.getBlockId()));
		blockScheduler.submitUnorderedBlock(middle, Set.of(genesis.getBlockId()));
		var ordering = blockScheduler.getValidOrdering(genesis);
		assertEquals(3, ordering.size());
		assertTrue(getOrderingIds(ordering).contains(last.getBlockId()));
	}

	@Test
	public void shouldNotReturnWhenDependingOnUnresolvedUnorderedBlock() {
		BlockchainNode genesis = genNoDependencies();
		BlockchainNode middle = genBlock(Set.of(genesis.getBlockId()));
		BlockchainNode last = genBlock(Set.of(middle.getBlockId()));
		blockScheduler.submitUnorderedBlock(last, Set.of(middle.getBlockId()));
		var ordering = blockScheduler.getValidOrdering(genesis);
		assertEquals(1, ordering.size());
		assertFalse(getOrderingIds(ordering).contains(last.getBlockId()));
	}

	@RepeatedTest(10)
	public void shouldNotPlaceOrderedBlocksBeforeTheirDependencies() {
		BlockchainNode genesis = genNoDependencies();
		BlockchainNode second = genBlock(Set.of(genesis.getBlockId()));
		BlockchainNode third = genBlock(Set.of(genesis.getBlockId(), second.getBlockId()));
		BlockchainNode last = genBlock(Set.of(second.getBlockId(), third.getBlockId()));
		blockScheduler.submitUnorderedBlock(second, Set.of(genesis.getBlockId()));
		blockScheduler.submitUnorderedBlock(third, Set.of(genesis.getBlockId(), second.getBlockId()));
		blockScheduler.submitUnorderedBlock(last, Set.of(second.getBlockId(), third.getBlockId()));
		var ordering = blockScheduler.getValidOrdering(genesis);
		assertEquals(4, ordering.size());
		assertEquals(genesis.getBlockId(), ordering.get(0).getBlockId());
		assertEquals(second.getBlockId(), ordering.get(1).getBlockId());
		assertEquals(third.getBlockId(), ordering.get(2).getBlockId());
		assertEquals(last.getBlockId(), ordering.get(3).getBlockId());
	}

}
