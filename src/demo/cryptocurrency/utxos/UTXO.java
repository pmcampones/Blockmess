package demo.cryptocurrency.utxos;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class UTXO implements Serializable {

	private final UUID id;

	private final int amount;

	/**
	 * Encoded Public Key of the UTXO owner.
	 * <p>Kept encoded to simplify the Serialization/Deserialization operations.</p>
	 */
	private final byte[] owner;

}
