package demo.cryptocurrency;

import lombok.Getter;
import lombok.SneakyThrows;
import utils.CryptographicUtils;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.PublicKey;
import java.util.Random;
import java.util.UUID;

@Getter
public class UTXO implements Serializable {

	private transient final UUID id;

	private final int nonce, amount;

	public UTXO(int amount, PublicKey origin, PublicKey destination) {
		this.amount = amount;
		this.nonce = new Random().nextInt();
		this.id = computeOutputUUID(origin, destination);
	}

	private UUID computeOutputUUID(PublicKey origin, PublicKey destination) {
		byte[] byteFields = getOutputFields(origin, destination);
		return CryptographicUtils.generateUUIDFromBytes(byteFields);
	}

	@SneakyThrows
	private byte[] getOutputFields(PublicKey origin, PublicKey destination) {
		try (var out = new ByteArrayOutputStream();
			 var oout = new ObjectOutputStream(out)) {
			oout.writeObject(origin);
			oout.writeObject(destination);
			oout.writeInt(nonce);
			oout.writeInt(amount);
			oout.flush();
			oout.flush();
			return out.toByteArray();
		}
	}

}
