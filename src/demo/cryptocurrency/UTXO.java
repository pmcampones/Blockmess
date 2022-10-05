package demo.cryptocurrency;

import lombok.Getter;
import lombok.SneakyThrows;
import utils.CryptographicUtils;

import java.io.*;
import java.security.PublicKey;
import java.util.Random;
import java.util.UUID;

@Getter
public class UTXO implements Serializable {

	private final UUID id;

	private final int nonce, amount;

	private final byte[] originEncoded, destinationEncoded;

	public UTXO(int amount, PublicKey origin, PublicKey destination) {
		this(amount, new Random().nextInt(), origin, destination);
	}

	public UTXO(int amount, int nonce, PublicKey origin, PublicKey destination) {
		this.amount = amount;
		this.nonce = nonce;
		this.originEncoded = origin.getEncoded();
		this.destinationEncoded = destination.getEncoded();
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
