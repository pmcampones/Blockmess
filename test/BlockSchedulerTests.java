import ledger.blockchain.BlockScheduler;
import ledger.blockchain.ScheduledBlock;
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
		var node = genNoDependencies();
		var ordering = blockScheduler.getValidOrdering(node);
		assertEquals(1, ordering.size());
		assertEquals(node.getId(), ordering.get(0).getId());
	}

	@NotNull
	private ScheduledBlock genNoDependencies() {
		return genBlock(Collections.emptySet());
	}

	private ScheduledBlock genBlock(Set<UUID> prev) {
		return new ScheduledBlock(UUID.randomUUID(), prev);
	}

	@Test
	public void shouldReturnUnorderedAfterSingleDependencyIsResolved() {
		ScheduledBlock genesis = genNoDependencies();
		ScheduledBlock unordered = genBlock(Set.of(genesis.getId()));
		blockScheduler.submitUnorderedBlock(unordered, Set.of(genesis.getId()));
		var ordering = blockScheduler.getValidOrdering(genesis);
		assertEquals(2, ordering.size());
		assertEquals(genesis.getId(), ordering.get(0).getId());
		assertEquals(unordered.getId(), ordering.get(1).getId());
	}

	@Test
	public void shouldNotReturnUnorderedUntilSingleDependencyIsResolved() {
		var ordered1 = genNoDependencies();
		var unordered1 = genBlock(Set.of(ordered1.getId()));
		var ordered2 = genNoDependencies();
		var unordered2 = genBlock(Set.of(ordered2.getId()));
		blockScheduler.submitUnorderedBlock(unordered1, Set.of(ordered1.getId()));
		blockScheduler.submitUnorderedBlock(unordered2, Set.of(ordered2.getId()));
		var ordering = blockScheduler.getValidOrdering(ordered1);
		List<UUID> orderingIds = getOrderingIds(ordering);
		assertEquals(2, ordering.size());
		assertFalse(orderingIds.contains(unordered2.getId()));
	}

	@NotNull
	private static List<UUID> getOrderingIds(List<ScheduledBlock> ordering) {
		return ordering.stream().map(ScheduledBlock::getId).collect(toList());
	}

	@Test
	public void shouldReturnUnorderedAfterAllDependenciesAreResolved() {
		var dependency1 = genNoDependencies();
		var dependency2 = genNoDependencies();
		var unordered = genBlock(Set.of(dependency1.getId(), dependency2.getId()));
		blockScheduler.submitUnorderedBlock(unordered, Set.of(dependency1.getId(), dependency2.getId()));
		blockScheduler.getValidOrdering(dependency1);
		var ordering = blockScheduler.getValidOrdering(dependency2);
		List<UUID> orderingIds = getOrderingIds(ordering);
		assertTrue(orderingIds.contains(unordered.getId()));
	}

	@Test
	public void shouldNotReturnUnorderedUntilAllDependenciesAreResolved() {
		var dependency1 = genNoDependencies();
		var dependency2 = genNoDependencies();
		var unordered = genBlock(Set.of(dependency1.getId(), dependency2.getId()));
		blockScheduler.submitUnorderedBlock(unordered, Set.of(dependency1.getId(), dependency2.getId()));
		var ordering = blockScheduler.getValidOrdering(dependency1);
		List<UUID> orderingIds = getOrderingIds(ordering);
		assertEquals(1, ordering.size());
		assertFalse(orderingIds.contains(unordered.getId()));
	}

	@Test
	public void shouldReturnWhenDependingOnResolvedUnorderedBlock() {
		var genesis = genNoDependencies();
		var middle = genBlock(Set.of(genesis.getId()));
		var last = genBlock(Set.of(middle.getId()));
		blockScheduler.submitUnorderedBlock(last, Set.of(middle.getId()));
		blockScheduler.submitUnorderedBlock(middle, Set.of(genesis.getId()));
		var ordering = blockScheduler.getValidOrdering(genesis);
		assertEquals(3, ordering.size());
		assertTrue(getOrderingIds(ordering).contains(last.getId()));
	}

	@Test
	public void shouldNotReturnWhenDependingOnUnresolvedUnorderedBlock() {
		var genesis = genNoDependencies();
		var middle = genBlock(Set.of(genesis.getId()));
		var last = genBlock(Set.of(middle.getId()));
		blockScheduler.submitUnorderedBlock(last, Set.of(middle.getId()));
		var ordering = blockScheduler.getValidOrdering(genesis);
		assertEquals(1, ordering.size());
		assertFalse(getOrderingIds(ordering).contains(last.getId()));
	}

	@RepeatedTest(10)
	public void shouldNotPlaceOrderedBlocksBeforeTheirDependencies() {
		var genesis = genNoDependencies();
		var second = genBlock(Set.of(genesis.getId()));
		var third = genBlock(Set.of(genesis.getId(), second.getId()));
		var last = genBlock(Set.of(second.getId(), third.getId()));
		blockScheduler.submitUnorderedBlock(second, Set.of(genesis.getId()));
		blockScheduler.submitUnorderedBlock(third, Set.of(genesis.getId(), second.getId()));
		blockScheduler.submitUnorderedBlock(last, Set.of(second.getId(), third.getId()));
		var ordering = blockScheduler.getValidOrdering(genesis);
		assertEquals(4, ordering.size());
		assertEquals(genesis.getId(), ordering.get(0).getId());
		assertEquals(second.getId(), ordering.get(1).getId());
		assertEquals(third.getId(), ordering.get(2).getId());
		assertEquals(last.getId(), ordering.get(3).getId());
	}

}
