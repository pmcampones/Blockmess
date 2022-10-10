import ledger.blockchain.Blockchain;
import ledger.blocks.BlockmessBlock;
import lombok.SneakyThrows;
import main.BlockmessLauncher;
import main.GlobalProperties;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.novasys.babel.core.Babel;

import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BlockchainTests {

	private static boolean isInitialized = false;

	private final Blockchain blockchain;

	@SneakyThrows
	public BlockchainTests() {
		if (!isInitialized) {
			Properties props = Babel.loadConfig(new String[]{}, BlockmessLauncher.DEFAULT_CONF);
			GlobalProperties.setProps(props);
			isInitialized = true;
		}
		blockchain = new Blockchain();
	}

	/**
	 * Verifies that at the start of the {@link Blockchain}'s execution, only the genesis block is present.
	 */
	@Test
	public void getLastBlockIsFirst() {
		Set<UUID> last = blockchain.getBlockR();
		assertEquals(1, last.size());
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
			BlockmessBlock block = createSimpleBlock(id);
			blockchain.submitBlock(block);
			Thread.sleep(100);
			lastId = block.getBlockId();
		}
	}

	private BlockmessBlock createSimpleBlock(UUID prev) {
		return new DummyBlockmessBlock(prev);
	}

}
