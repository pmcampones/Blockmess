import ledger.blockchain.BlockFinalizer;
import ledger.blockchain.Blockchain;
import lombok.SneakyThrows;
import main.BlockmessLauncher;
import main.GlobalProperties;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
	public void getLastBlockIsFirst() {
		Set<UUID> last = blockchain.getBlockR();
		assertEquals(1, last.size());
		assertEquals(new UUID(0, 0), new ArrayList<>(last).get(0));
	}

	/**
	 * Submits several blocks to a single chain and requests the reference to the block at the end of the chain.
	 * Verifies that the correct block is returned.
	 */
	@Test
	void getLastBlockSeveralInSingleChain() throws Exception {
		UUID lastId = blockchain.getBlockR().iterator().next();
		for (int i = 0; i < 10; i++) {
			Set<UUID> last = blockchain.getBlockR();
			assertEquals(1, last.size());
			UUID id = last.iterator().next();
			assertEquals(lastId, id);
			UUID newNodeId = UUID.randomUUID();
			blockchain.addBlock(newNodeId, Set.of(id), 1);
			Thread.sleep(100);
			lastId = newNodeId;
		}
	}

}
