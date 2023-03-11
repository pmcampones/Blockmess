package validators;

import ledger.blocks.BlockmessBlock;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.tuple.Pair;

public class FixedApplicationAwareValidator implements ApplicationAwareValidator {

	private static FixedApplicationAwareValidator singleton;

	@Delegate
	private ApplicationAwareValidator validator = new DefaultApplicationAwareValidator();

	public static FixedApplicationAwareValidator getSingleton() {
		if (singleton == null)
			singleton = new FixedApplicationAwareValidator();
		return singleton;
	}

	public void setCustomValidator(ApplicationAwareValidator validator) {
		this.validator = validator;
	}
}
