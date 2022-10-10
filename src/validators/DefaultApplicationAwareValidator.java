package validators;

import ledger.blocks.BlockmessBlock;
import org.apache.commons.lang3.tuple.Pair;

/**
 * @author elcampones
 */
public class DefaultApplicationAwareValidator implements ApplicationAwareValidator {
	@Override
	public Pair<Boolean, byte[]> validateReceivedOperation(byte[] operation) {
		return Pair.of(true, new byte[0]);
	}

	@Override
	public boolean validateBlockContent(BlockmessBlock block) {
		return true;
	}
}
