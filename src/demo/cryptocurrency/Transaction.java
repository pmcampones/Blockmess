package demo.cryptocurrency;

import lombok.Getter;
import lombok.SneakyThrows;
import utils.CryptographicUtils;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

@Getter
public class Transaction {

	private transient final UUID id;

	private transient final byte[] hashVal;

	private final PublicKey origin, destination;

	/**
	 * Identifiers of the UTXO inputs used in the transaction.
	 */
	private final List<UUID> inputs;

	/**
	 * List of simplified UTXOs belonging to the destination of the transaction.
	 */
	private final List<UTXO> outputsDestination;

	/**
	 * List of simplified UTXOs belonging to the originator of the transaction. These are used for the origin to obtain
	 * the excess UTXO coins sent in the input.
	 */
	private final List<UTXO> outputsOrigin;

	private final byte[] originSignature;

	public Transaction(PublicKey origin, PublicKey destination, List<UUID> inputs,
					   List<Integer> outputsDestinationAmount, List<Integer> outputsOriginAmount, PrivateKey signer) {
		this.origin = origin;
		this.destination = destination;
		this.inputs = inputs;
		this.outputsDestination = getUTXOsFromAmounts(origin, destination, outputsDestinationAmount);
		this.outputsOrigin = getUTXOsFromAmounts(origin, destination, outputsOriginAmount);
		this.hashVal = obtainTxByteFields();
		this.id = CryptographicUtils.generateUUIDFromBytes(hashVal);
		this.originSignature = CryptographicUtils.getFieldsSignature(hashVal, signer);
	}

	private static List<UTXO> getUTXOsFromAmounts(PublicKey origin, PublicKey destination, List<Integer> outputsOriginAmount) {
		return outputsOriginAmount.stream().map(val -> new UTXO(val, origin, destination)).collect(toList());
	}

	@SneakyThrows
	private byte[] obtainTxByteFields() {
		try (var out = new ByteArrayOutputStream();
			 var oout = new ObjectOutputStream(out)) {
			oout.writeObject(origin);
			oout.writeObject(destination);
			for (UUID input : inputs) oout.writeObject(input);
			for (UTXO outD : outputsDestination)
				oout.writeObject(outD.getId());
			for (UTXO outO : outputsOrigin)
				oout.writeObject(outO.getId());
			oout.flush();
			return out.toByteArray();
		}
	}

}
