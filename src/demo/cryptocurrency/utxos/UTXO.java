package demo.cryptocurrency.utxos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.*;
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

	@SneakyThrows
	public static byte[] serializeUTXO(UTXO utxo) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
			 ObjectOutputStream oout = new ObjectOutputStream(out)) {
			oout.writeObject(utxo);
			oout.flush();
			out.flush();
			return out.toByteArray();
		}
	}

	@SneakyThrows
	public static UTXO deserializeUTXO(byte[] bytes) {
		try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
			 ObjectInputStream oin = new ObjectInputStream(in)) {
			return (UTXO) oin.readObject();
		}
	}

}
