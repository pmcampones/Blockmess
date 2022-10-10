import ledger.blockchain.BlockFinalizer;
import ledger.blockchain.Blockchain;
import lombok.SneakyThrows;
import main.BlockmessLauncher;
import main.GlobalProperties;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class BlockFinalizerTests {

	private static boolean isInitialized = false;

	private final BlockFinalizer blockchain;

	@SneakyThrows
	public BlockFinalizerTests() {
		if (!isInitialized) {
			Properties props = Babel.loadConfig(new String[]{}, BlockmessLauncher.DEFAULT_CONF);
			GlobalProperties.setProps(props);
			isInitialized = true;
		}
		blockchain = new BlockFinalizer(new UUID(0, 0));
	}

	/**
	 * Verifies that at the start of the {@link Blockchain}'s execution, only the genesis block is present.
	 */
	@Test
	public void shouldGetLastBlockFirst() {
		Set<UUID> last = blockchain.getBlockR();
		assertEquals(1, last.size());
		assertEquals(new UUID(0, 0), new ArrayList<>(last).get(0));
	}

	/**
	 * Submits several blocks to a single chain and requests the reference to the block at the end of the chain.
	 * Verifies that the correct block is returned.
	 */
	@Test
	void shouldGetLastBlockInSingleChainWithSeveralBlocks() {
		UUID lastId = blockchain.getBlockR().iterator().next();
		for (int i = 0; i < 10; i++) {
			Set<UUID> last = blockchain.getBlockR();
			assertEquals(1, last.size());
			UUID id = last.iterator().next();
			assertEquals(lastId, id);
			lastId = addBlock(id);
		}
	}

	private UUID addBlock(UUID prev) {
		UUID id = UUID.randomUUID();
		blockchain.addBlock(id, Set.of(prev), 1);
		return id;
	}

	/**
	 * Creates a fork in the chain and requests the last block. The two resulting chains have the same length. One of
	 * the two tips should be returned.
	 */
	@Test
	void shoudGetOneOfTwoLastBlocks() {
		Set<UUID> genesisSet = blockchain.getBlockR();
		UUID genesis = genesisSet.iterator().next();
		UUID b1 = addBlock(genesis);
		UUID b2 = addBlock(genesis);
		Set<UUID> last = blockchain.getBlockR();
		assertEquals(1, last.size());
		UUID id = last.iterator().next();
		assertTrue(id.equals(b1) || id.equals(b2));
	}

	/**
	 * Creates a fork in the chain and requests the last block reference twice. Blocks are appended to the two forks
	 * such that on each request for the last block a different chain should be queried.
	 **/
	@RepeatedTest(10)
	void shouldGetBlockInLongestChain() {
		Set<UUID> genesisSet = blockchain.getBlockR();
		UUID genesis = genesisSet.iterator().next();
		UUID lastB1 = addBlock(genesis);
		lastB1 = addBlock(lastB1);
		UUID lastB2 = addBlock(genesis);
		Set<UUID> last1 = blockchain.getBlockR();
		assertEquals(1, last1.size());
		UUID l1 = last1.iterator().next();
		assertEquals(l1, lastB1);
		lastB2 = addBlock(lastB2);
		lastB2 = addBlock(lastB2);
		Set<UUID> last2 = blockchain.getBlockR();
		assertEquals(1, last2.size());
		UUID l2 = last2.iterator().next();
		assertEquals(lastB2, l2);
	}

	/**
	 * Issues several blocks in a single chain following the logic the IntermediateConsensus follows when assigning a
	 * previous to a new block. Verifies that all blocks are contained in the {@link Blockchain}.
	 */
	@Test
	void shouldHaveAllBlocksInForklessChain() {
		Set<UUID> blocks = new HashSet<>(11);
		blocks.add(blockchain.getBlockR().iterator().next());
		for (int i = 1; i < 10; i++) {
			Set<UUID> last = blockchain.getBlockR();
			UUID block = addBlock(last.iterator().next());
			blocks.add(block);
		}
		assertEquals(blocks, blockchain.getNodesIds());
	}

	/**
	 * Creates a fork in the chain and issues new block to both forks. This is done alternately so that blocks are not
	 * discarded. Upon the conclusion of the block submission its verified that all blocks are contained in the
	 * {@link Blockchain}.
	 */
	@Test
	void shouldHaveAllBlocksInForkedChain() {
		Set<UUID> blocks = new HashSet<>(11);
		blocks.add(blockchain.getBlockR().iterator().next());
		for (int i = 1; i < 11; i += 2) {
			Set<UUID> last = blockchain.getBlockR();
			UUID b1 = addBlock(last.iterator().next());
			blocks.add(b1);
			UUID b2 = addBlock(last.iterator().next());
			blocks.add(b2);
		}
		assertEquals(blocks, blockchain.getNodesIds());
	}

	/**
	 * Creates a fork in the chain and expands a single chain, such that the single block in one of the chains is
	 * discarded. Verifies that the block expected to be discarded is not contained in the {@link Blockchain}
	 */
	@Test
	void shouldHaveAllBlocksExceptOnDiscardedFork() {
		Set<UUID> blocks = new HashSet<>();
		blocks.add(blockchain.getBlockR().iterator().next());
		Set<UUID> genesisSet = blockchain.getBlockR();
		UUID discarded = addBlock(genesisSet.iterator().next());
		UUID b1 = addBlock(genesisSet.iterator().next());
		blocks.add(b1);
		UUID b2 = addBlock(b1);
		blocks.add(b2);
		for (int i = 3; i < 11; i++) {
			Set<UUID> last = blockchain.getBlockR();
			UUID nextBlock = addBlock(last.iterator().next());
			blocks.add(nextBlock);
		}
		assertEquals(blocks, blockchain.getNodesIds());
	}

	/**
	 * Forks the chain and expands both chains. It's verified that the Blockchain contains all blocks, but only the
	 * genesis is finalized. Then, a single chain is expanded such that the other chain is discarded. We verify that no
	 * block from the discarded chain figures in the {@link Blockchain}, and that the blocks of the longest chain are
	 * finalized.
	 */
	@Test
	void shouldOnlyKeepOneForkWhenTwoLargeForksExistAndOneIsDiscarded() {
		UUID l1, l2;
		l1 = l2 = blockchain.getBlockR().iterator().next();
		for (int i = 0; i < 50; i++) {
			l1 = addBlock(l1);
			l2 = addBlock(l2);
		}
		assertEquals(1, blockchain.getFinalizedIds().size());
		assertEquals(1 + 2 * 50, blockchain.getNodesIds().size());
		for (int i = 0; i < blockchain.getFinalizedWeight(); i++) {
			addBlock(blockchain.getBlockR().iterator().next());
		}
		assertEquals(50 + 1, blockchain.getFinalizedIds().size());
		assertEquals(50 + 1 + blockchain.getFinalizedWeight(), blockchain.getNodesIds().size());
	}

	/**
	 * Creates several forks in the chain. This is done by submitting pairs of forking blocks. Verifies that the blocks
	 * are discarded and finalized exactly when it is expected.
	 */
	@Test
	void shouldGraduallyDiscardForksAsTheseAreOvertaken() {
		for (int i = 0; i < blockchain.getFinalizedWeight(); i++) {
			Set<UUID> last = blockchain.getBlockR();
			addBlock(last.iterator().next());
			addBlock(last.iterator().next());
		}
		assertEquals(1 + 2 * blockchain.getFinalizedWeight(), blockchain.getNodesIds().size());
		assertEquals(1, blockchain.getFinalizedIds().size());
		for (int i = 0; i < 20; i++) {
			Set<UUID> last = blockchain.getBlockR();
			addBlock(last.iterator().next());
			addBlock(last.iterator().next());
			assertEquals(i + 2, blockchain.getFinalizedIds().size());
		}
	}

	@Test
	void shouldFindGenesisInLongestChain() {
		UUID genesis = blockchain.getBlockR().iterator().next();
		addBlock(genesis);
		assertTrue(blockchain.isInLongestChain(genesis));
		for (int i = 0; i < blockchain.getFinalizedWeight(); i++) {
			addBlock(blockchain.getBlockR().iterator().next());
		}
		assertTrue(blockchain.isInLongestChain(genesis));
	}

	@Test
	void shouldFindBlockInLongestChainWhenForksAreTied() {
		var block1 = addBlock(blockchain.getBlockR().iterator().next());
		var block2 = addBlock(blockchain.getBlockR().iterator().next());
		assertTrue(blockchain.isInLongestChain(block1));
		assertTrue(blockchain.isInLongestChain(block2));
	}

	@Test
	void shouldNotFindInLongestChain() {
		UUID genesis = blockchain.getBlockR().iterator().next();
		var block1 = addBlock(genesis);
		var block2 = addBlock(genesis);
		addBlock(block2);
		assertFalse(blockchain.isInLongestChain(block1));
	}


}
