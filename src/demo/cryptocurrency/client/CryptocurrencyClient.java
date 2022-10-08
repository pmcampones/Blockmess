package demo.cryptocurrency.client;

import applicationInterface.ApplicationInterface;
import applicationInterface.ReplyListener;
import cmux.FixedCMuxIdMapper;
import demo.cryptocurrency.CryptocurrencyCMuxMapper;
import demo.cryptocurrency.DBAdapter;
import demo.cryptocurrency.Transaction;
import demo.cryptocurrency.TransactionValidator;
import demo.cryptocurrency.outputLogging.DiscardedBlocksLog;
import demo.cryptocurrency.outputLogging.FinalizedBlocksLog;
import demo.cryptocurrency.outputLogging.FinalizedTransactionLog;
import demo.cryptocurrency.outputLogging.UnfinalizedBlocksLog;
import demo.cryptocurrency.utxos.InTransactionUTXO;
import demo.cryptocurrency.utxos.UTXO;
import demo.cryptocurrency.utxos.UTXOProcessor;
import ledger.blocks.BlockmessBlock;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.CryptographicUtils;
import validators.FixedApplicationAwareValidator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
		FixedCMuxIdMapper.getSingleton().setCustomMapper(new CryptocurrencyCMuxMapper());
		FixedApplicationAwareValidator.getSingleton().setCustomValidator(new TransactionValidator());
		this.myUTXOs = filterMyUTXOs();
		this.unfinalizedLog = new UnfinalizedBlocksLog();
		this.finalizedLog = new FinalizedBlocksLog();
		this.discardedLog = new DiscardedBlocksLog();
		this.txLog = new FinalizedTransactionLog();
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
		List<Integer> outputsOriginAmount = change == 0 ? Collections.emptyList() : List.of(change);
		Transaction tx = new Transaction(myKeys.getPublic(), destination,
				inputIds, List.of(amount), outputsOriginAmount, myKeys.getPrivate());
		inputIds.forEach(myUTXOs::remove);
		super.invokeAsyncOperation(serializeTx(tx), listener);
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

	@SneakyThrows
	private byte[] serializeTx(Transaction tx) {
		try (var out = new ByteArrayOutputStream(); var oout = new ObjectOutputStream(out)) {
			oout.writeObject(tx);
			oout.flush();
			out.flush();
			return out.toByteArray();
		}
	}

	@Override
	@SneakyThrows
	public byte[] processOperation(byte[] operation) {
		Transaction tx = deserializeTx(operation);
		logger.debug("Processing delivered transaction: {}", tx.getId());
		DBAdapter db = DBAdapter.getSingleton();
		db.deleteUTXOs(tx.getInputs());
		PublicKey og = CryptographicUtils.fromEncodedFormat(tx.getOrigin());
		PublicKey dest = CryptographicUtils.fromEncodedFormat(tx.getDestination());
		List<UTXO> utxos = new ArrayList<>(tx.getOutputsDestination().size() + tx.getOutputsOrigin().size());
		for (InTransactionUTXO inUTXO : tx.getOutputsDestination())
			utxos.add(UTXOProcessor.processTransactionUTXO(inUTXO, og, dest));
		for (InTransactionUTXO inUTXO : tx.getOutputsOrigin())
			utxos.add(UTXOProcessor.processTransactionUTXO(inUTXO, og, dest));
		db.submitUTXOs(utxos);
		txLog.logFinalizedTransaction(tx);
		return new byte[0];
	}

	@SneakyThrows
	private Transaction deserializeTx(byte[] encodedTx) {
		try (var in = new ByteArrayInputStream(encodedTx); var oin = new ObjectInputStream(in)) {
			return (Transaction) oin.readObject();
		}
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
