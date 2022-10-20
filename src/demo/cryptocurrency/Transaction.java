package demo.cryptocurrency;

import cyclops.control.Eval;
import demo.cryptocurrency.utxos.InTransactionUTXO;
import demo.cryptocurrency.utxos.UTXOProcessor;
import lombok.Getter;
import lombok.SneakyThrows;
import utils.CryptographicUtils;

import java.io.*;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

@Getter
public class Transaction implements Serializable {

	private final PublicKey origin, destination;
	/**
	 * Identifiers of the UTXO inputs used in the transaction.
	 */
	private final List<UUID> inputs;
	/**
	 * List of simplified UTXOs belonging to the destination of the transaction.
	 */
	private final List<InTransactionUTXO> outputsDestination;
	/**
	 * List of simplified UTXOs belonging to the originator of the transaction. These are used for the origin to obtain
	 * the excess UTXO coins sent in the input.
	 */
	private final List<InTransactionUTXO> outputsOrigin;
	private final byte[] originSignature;
	private transient Eval<UUID> id = Eval.eval(this::genTxId);

	public Transaction(PublicKey origin, PublicKey destination, List<UUID> inputs,
					   List<Integer> outputsDestinationAmount, List<Integer> outputsOriginAmount, PrivateKey signer) {
		this.origin = origin;
		this.destination = destination;
		this.inputs = inputs;
		this.outputsDestination = getUTXOsFromAmounts(outputsDestinationAmount);
		this.outputsOrigin = getUTXOsFromAmounts(outputsOriginAmount);
		byte[] hashVal = obtainTxByteFields();
		this.originSignature = CryptographicUtils.getFieldsSignature(hashVal, signer);
	}

	private static List<InTransactionUTXO> getUTXOsFromAmounts(List<Integer> outputsOriginAmount) {
		Random rand = new Random();
		return outputsOriginAmount.stream().map(val -> new InTransactionUTXO(rand.nextInt(), val)).collect(toList());
	}

	@SneakyThrows
	public byte[] obtainTxByteFields() {
		try (var out = new ByteArrayOutputStream();
			 var oout = new ObjectOutputStream(out)) {
			oout.writeObject(origin);
			oout.writeObject(destination);
			for (UUID input : inputs) oout.writeObject(input);
			for (InTransactionUTXO outD : outputsDestination)
				oout.writeObject(UTXOProcessor.processTransactionUTXO(outD, origin, destination).getId());
			for (InTransactionUTXO outO : outputsOrigin)
				oout.writeObject(UTXOProcessor.processTransactionUTXO(outO, origin, destination).getId());
			oout.flush();
			out.flush();
			return out.toByteArray();
		}
	}

	@SneakyThrows
	public static byte[] serializeTx(Transaction tx) {
		try (var out = new ByteArrayOutputStream(); var oout = new ObjectOutputStream(out)) {
			oout.writeObject(tx);
			oout.flush();
			out.flush();
			return out.toByteArray();
		}
	}

	@SneakyThrows
	public static Transaction deserializeTx(byte[] txBytes) {
		try (var in = new ByteArrayInputStream(txBytes); var oin = new ObjectInputStream(in)) {
			return (Transaction) oin.readObject();
		}
	}

	public int getSerializedSize() {
		return origin.getEncoded().length + destination.getEncoded().length + originSignature.length
				+ inputs.size() * 2 * Long.BYTES
				+ (outputsDestination.size() + outputsOrigin.size()) * 2 * Integer.BYTES;
	}

	public UUID getId() {
		if (id == null)    //This happens after deserializing a transaction
			id = Eval.eval(this::genTxId);
		return id.get();
	}

	private UUID genTxId() {
		return CryptographicUtils.generateUUIDFromBytes(obtainTxByteFields());
	}

}
