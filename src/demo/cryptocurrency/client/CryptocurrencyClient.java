package demo.cryptocurrency.client;

import applicationInterface.ApplicationInterface;
import applicationInterface.ReplyListener;
import demo.cryptocurrency.DBAdapter;
import demo.cryptocurrency.Transaction;
import demo.cryptocurrency.TransactionValidator;
import demo.cryptocurrency.outputLogging.*;
import demo.cryptocurrency.utxos.InTransactionUTXO;
import demo.cryptocurrency.utxos.UTXO;
import demo.cryptocurrency.utxos.UTXOProcessor;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.LedgerManager;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.CryptographicUtils;
import validators.FixedApplicationAwareValidator;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Getter
public class CryptocurrencyClient extends ApplicationInterface {

	private static final Logger logger = LogManager.getLogger(CryptocurrencyClient.class);

	private final KeyPair myKeys;

	private final Map<UUID, UTXO> myUTXOs;

	private final UnfinalizedBlocksLog unfinalizedLog;

	private final FinalizedBlocksLog finalizedLog;

	private final DiscardedBlocksLog discardedLog;

	private final FinalizedTransactionLog txLog;


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
		this.myKeys = CryptographicUtils.readECDSAKeyPair();
		//FixedCMuxIdMapper.getSingleton().setCustomMapper(new CryptocurrencyCMuxMapper());
		FixedApplicationAwareValidator.getSingleton().setCustomValidator(new TransactionValidator());
		this.myUTXOs = filterMyUTXOs();
		this.unfinalizedLog = new UnfinalizedBlocksLog();
		this.finalizedLog = new FinalizedBlocksLog();
		this.discardedLog = new DiscardedBlocksLog();
		this.txLog = new FinalizedTransactionLog();
		LedgerManager.getSingleton().addChangesChainsObserver(new ChangesChainsLog());
	}

	private Map<UUID, UTXO> filterMyUTXOs() {
		List<UTXO> allUtxos = DBAdapter.getSingleton().getUTXOs(Integer.MAX_VALUE);
		return allUtxos.parallelStream()
				.filter(utxo -> Arrays.equals(utxo.getOwner(), myKeys.getPublic().getEncoded()))
				.collect(Collectors.toMap(UTXO::getId, u -> u));
	}

	public void submitTx(PublicKey destination, int amount, ReplyListener listener) throws InsufficientFundsException {
		List<UTXO> inputs = getUTXOInputs(amount);
		List<UUID> inputIds = inputs.stream().map(UTXO::getId).collect(toList());
		int change = amount - inputs.stream().mapToInt(UTXO::getAmount).sum();
		List<Integer> outputsOriginAmount = change == 0 ? Collections.emptyList() : List.of(-change);
		Transaction tx = new Transaction(myKeys.getPublic(), destination,
				inputIds, List.of(amount), outputsOriginAmount, myKeys.getPrivate());
		inputIds.forEach(myUTXOs::remove);
		logger.debug("Submitting Transaction {}", tx.getId());
		super.invokeAsyncOperation(Transaction.serializeTx(tx), listener);
	}

	private List<UTXO> getUTXOInputs(int amount) throws InsufficientFundsException {
		int currAmount = 0;
		List<UTXO> inputUtxo = new ArrayList<>();
		Iterator<UTXO> myUTXOsIt = myUTXOs.values().iterator();
		while (myUTXOsIt.hasNext() && currAmount < amount) {
			UTXO utxo = myUTXOsIt.next();
			currAmount += utxo.getAmount();
			inputUtxo.add(utxo);
		}
		if (currAmount < amount)
			throw new InsufficientFundsException(amount, currAmount);
		return inputUtxo;
	}

	@Override
	@SneakyThrows
	public byte[] processOperation(byte[] operation) {
		Transaction tx = Transaction.deserializeTx(operation);
		logger.debug("Received tx: {}", tx.getId());
		return TransactionValidator.isFinalizedBlockValid(tx) ?
				processValidTransaction(tx) : "Invalid Tx".getBytes();
	}

	private byte[] processValidTransaction(Transaction tx) throws IOException {
		logger.debug("Processing tx: {}", tx.getId());
		DBAdapter db = DBAdapter.getSingleton();
		db.deleteUTXOs(tx.getInputs());
		PublicKey og = tx.getOrigin();
		PublicKey dest = tx.getDestination();
		List<UTXO> utxos = new ArrayList<>(tx.getOutputsDestination().size() + tx.getOutputsOrigin().size());
		for (InTransactionUTXO inUTXO : tx.getOutputsDestination())
			utxos.add(UTXOProcessor.processTransactionUTXO(inUTXO, og, dest));
		for (InTransactionUTXO inUTXO : tx.getOutputsOrigin())
			utxos.add(UTXOProcessor.processTransactionUTXO(inUTXO, og, dest));
		db.submitUTXOs(utxos);
		TransactionValidator.forgetTxValidation(tx);
		txLog.logFinalizedTransaction(tx);
		return dest.getEncoded();
	}

	@Override
	public void notifyNonFinalizedBlock(BlockmessBlock block) {
		unfinalizedLog.logUnfinalizedBlock(block);
	}

	@Override
	public void notifyFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
		finalizedLog.logFinalizedBlock(finalized);
		discardedLog.logDiscardedBlock(discarded);
	}

}
