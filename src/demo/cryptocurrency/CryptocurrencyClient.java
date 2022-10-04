package demo.cryptocurrency;

import applicationInterface.ApplicationInterface;
import cmux.FixedCMuxIdMapper;
import validators.FixedApplicationAwareValidator;

public class CryptocurrencyClient extends ApplicationInterface {

	/**
	 * The creation of the ApplicationInterface triggers the launch of Blockmess.
	 * <p>Upon the creation of this class, this replica will connect to others according with the launch
	 * configurations, and is then ready to submit, receive, and execute operations and blocks</p>
	 *
	 * @param blockmessProperties A list of properties that override those in the default configuration file.
	 *                            <p> This file is found in "${PWD}/config/config.properties"</p>
	 */
	public CryptocurrencyClient(String[] blockmessProperties) {
		super(blockmessProperties);
		FixedCMuxIdMapper.getSingleton().setCustomMapper(new CryptocurrencyCMuxMapper());
		FixedApplicationAwareValidator.getSingleton().setCustomValidator(new TransactionValidator());
	}

	@Override
	public byte[] processOperation(byte[] operation) {
		return new byte[0];
	}
}
