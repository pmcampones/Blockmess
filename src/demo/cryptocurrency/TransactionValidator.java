package demo.cryptocurrency;

import cmux.AppOperation;
import demo.cryptocurrency.utxos.InTransactionUTXO;
import demo.cryptocurrency.utxos.UTXO;
import ledger.blocks.BlockmessBlock;
import org.apache.commons.lang3.tuple.Pair;
import utils.CryptographicUtils;
import validators.ApplicationAwareValidator;

import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Validates transactions.
 */
public class TransactionValidator implements ApplicationAwareValidator {

	/**
	 * Caches which transactions have been validated.
	 * <p>Avoids redundant validations when a block is proposed.</p>
	 */
	private static final Set<UUID> validatedTxs = new HashSet<>();

	public static boolean isFinalizedBlockValid(Transaction tx) {
		Collection<UTXO> inputs = getAllInputs(tx);
		if (!hasAllInputs(inputs, tx) || !areInputsFromTxIssuer(tx, inputs))
			return false;
		Collection<InTransactionUTXO> outputs = getAllOutputs(tx);
		return amountsMatch(inputs, outputs);
	}

	private static Collection<UTXO> getAllInputs(Transaction tx) {
		List<UUID> inputsIds = tx.getInputs();
		return DBAdapter.getSingleton().getUTXOs(inputsIds);
	}

	private static boolean hasAllInputs(Collection<UTXO> inputs, Transaction tx) {
		return inputs.size() == tx.getInputs().size();
	}

	private static boolean amountsMatch(Collection<UTXO> inputs, Collection<InTransactionUTXO> outputs) {
		int inputAmounts = inputs.stream().mapToInt(UTXO::getAmount).sum();
		int outputAmounts = outputs.stream().mapToInt(InTransactionUTXO::getAmount).sum();
		return inputAmounts == outputAmounts;
	}

	private static boolean areInputsFromTxIssuer(Transaction tx, Collection<UTXO> inputs) {
		byte[] issuer = tx.getOrigin().getEncoded();
		return inputs.stream().map(UTXO::getOwner).allMatch(owner -> Arrays.equals(owner, issuer));
	}

	private static List<InTransactionUTXO> getAllOutputs(Transaction tx) {
		return Stream.concat(tx.getOutputsDestination().stream(), tx.getOutputsOrigin().stream()).collect(toList());
	}

	public static void forgetTxValidation(Transaction tx) {
		validatedTxs.remove(tx.getId());
	}

	@Override
	public Pair<Boolean, byte[]> validateReceivedOperation(byte[] operation) {
		return validateUnfinalizedTx(operation);
	}

	private static Pair<Boolean, byte[]> validateUnfinalizedTx(byte[] operation) {
		Transaction tx = Transaction.deserializeTx(operation);
		UUID txId = tx.getId();
		if (validatedTxs.contains(txId))
			return Pair.of(true, new byte[0]);
		Collection<InTransactionUTXO> outputs = getAllOutputs(tx);
		if (outputsHaveNegativeAmounts(outputs))
			return Pair.of(false, "One UTXO output has negative value".getBytes());
		if (!signatureMatches(tx))
			return Pair.of(false, "The signature of the Transation does not match its content".getBytes());
		validatedTxs.add(tx.getId());
		return Pair.of(true, new byte[0]);
	}

	private static boolean outputsHaveNegativeAmounts(Collection<InTransactionUTXO> outputs) {
		return outputs.stream().mapToInt(InTransactionUTXO::getAmount).anyMatch(x -> x <= 0);
	}

	private static boolean signatureMatches(Transaction tx) {
		try {
			return tryToMatchSignature(tx);
		} catch (InvalidKeyException | SignatureException e) {
			return false;
		}
	}

	private static boolean tryToMatchSignature(Transaction tx) throws InvalidKeyException, SignatureException {
		byte[] reportedSignature = tx.getOriginSignature();
		byte[] txContents = tx.obtainTxByteFields();
		return CryptographicUtils.verifyPojoSignature(reportedSignature, txContents, tx.getOrigin());
	}

	@Override
	public boolean validateBlockContent(BlockmessBlock block) {
		return block.getContentList().getContentList().parallelStream()
				.map(AppOperation::getContent)
				.map(TransactionValidator::validateUnfinalizedTx)
				.allMatch(Pair::getLeft);
	}

}
