package demo.cryptocurrency.utxos;


import utils.CryptographicUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.PublicKey;
import java.util.UUID;

public class UTXOProcessor {

	public static UTXO processTransactionUTXO(InTransactionUTXO txUtxo, PublicKey origin, PublicKey destination) throws IOException {
		UUID utxoId = computeOutputUUID(txUtxo, origin, destination);
		return new UTXO(utxoId, txUtxo.getAmount(), destination.getEncoded());
	}

	private static UUID computeOutputUUID(InTransactionUTXO txUtxo, PublicKey origin, PublicKey destination) throws IOException {
		byte[] byteFields = getOutputFields(txUtxo, origin, destination);
		return CryptographicUtils.generateUUIDFromBytes(byteFields);
	}

	private static byte[] getOutputFields(InTransactionUTXO txUtxo, PublicKey origin, PublicKey destination) throws IOException {
		try (var out = new ByteArrayOutputStream();
			 var oout = new ObjectOutputStream(out)) {
			oout.writeObject(origin);
			oout.writeObject(destination);
			oout.writeInt(txUtxo.getNonce());
			oout.writeInt(txUtxo.getAmount());
			oout.flush();
			oout.flush();
			return out.toByteArray();
		}
	}

}
