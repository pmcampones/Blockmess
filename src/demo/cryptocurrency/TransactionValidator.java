package demo.cryptocurrency;

import ledger.blocks.BlockmessBlock;
import org.apache.commons.lang3.tuple.Pair;
import validators.ApplicationAwareValidator;

public class TransactionValidator implements ApplicationAwareValidator {

	@Override
	public Pair<Boolean, byte[]> validateReceivedOperation(byte[] operation) {
		return Pair.of(true, new byte[0]);
	}

	@Override
	public boolean validateBlockContent(BlockmessBlock block) {
		return false;
	}
}
