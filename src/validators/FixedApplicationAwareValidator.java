package validators;

import ledger.blocks.BlockmessBlock;
import org.apache.commons.lang3.tuple.Pair;

public class FixedApplicationAwareValidator implements ApplicationAwareValidator {

	private static FixedApplicationAwareValidator singleton;

	private ApplicationAwareValidator validator = new DefaultApplicationAwareValidator();

	public static FixedApplicationAwareValidator getSingleton() {
		if (singleton == null)
			singleton = new FixedApplicationAwareValidator();
		return singleton;
	}

	@Override
	public Pair<Boolean, byte[]> validateReceivedOperation(byte[] operation) {
		return validator.validateReceivedOperation(operation);
	}

	@Override
	public boolean validateBlockContent(BlockmessBlock block) {
		return validator.validateBlockContent(block);
	}

	public void setCustomValidator(ApplicationAwareValidator validator) {
		this.validator = validator;
	}
}
