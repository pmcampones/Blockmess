package applicationInterface;

import cmux.AppOperation;
import ledger.LedgerObserver;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.LedgerManager;
import lombok.SneakyThrows;
import mempoolManager.MempoolManager;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.thavam.util.concurrent.blockingMap.BlockingHashMap;
import org.thavam.util.concurrent.blockingMap.BlockingMap;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import utils.IDGenerator;
import validators.FixedApplicationAwareValidator;
import valueDispatcher.ValueDispatcher;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import static org.bouncycastle.pqc.math.linearalgebra.ByteUtils.concatenate;

/**
 * Interface separating the Blockmess internals from the application logic.
 * <p>The application submits operations in a generic format to this class,
 * which then submits it to Blockmess proper.</p>
 * <p>This class maintains a record of the operations submitted by the client connected to this replica.
 * When one such operation is executed, the result of the operation is delivered to the application.</p>
 * <p>The underlying Blockmess structure is aware this class is connected to the application,
 * as such application specific logic is submitted to this abstract class. In particular, the processing of the
 * operations is submitted here.</p>
 * <p>In order to compute this application logic, the application must extend this class.</p>
 */
public abstract class ApplicationInterface extends GenericProtocol implements LedgerObserver {

	private final AtomicLong localOpIdx = new AtomicLong(0);
	private final BlockingMap<UUID, Pair<byte[], Long>> completedSyncOperations = new BlockingHashMap<>();
	private final Map<UUID, ReplyListener> operationListeners = new ConcurrentHashMap<>();
	private final Set<UUID> operationsWaitingResponse = ConcurrentHashMap.newKeySet();
	private final BlockingQueue<AppOperation> queuedOperations = new LinkedBlockingQueue<>();
	private long globalOpIdx = 0;
	private byte[] replicaId;

	/**
	 * The creation of the ApplicationInterface triggers the launch of Blockmess.
	 * <p>Upon the creation of this class, this replica will connect to others according with the launch
	 * configurations, and is then ready to submit, receive, and execute operations and blocks</p>
	 *
	 * @param blockmessProperties A list of properties that override those in the default configuration file.
	 *                            <p> This file is found in "${PWD}/config/config.properties"</p>
	 */
	@SneakyThrows
	public ApplicationInterface(@NotNull String[] blockmessProperties) {
		super(ApplicationInterface.class.getSimpleName(), IDGenerator.genId());
		subscribeNotification(DeliverFinalizedContentNotification.ID, this::uponDeliverFinalizedContentNotification);
		BlockmessLauncher.launchBlockmess(blockmessProperties, this);
	}

	private void uponDeliverFinalizedContentNotification(DeliverFinalizedContentNotification notif, short source) {
		queuedOperations.addAll(notif.getFinalizedContent());
	}

	@Override
	public void init(Properties props) {
		String address = props.getProperty("address");
		int port = Integer.parseInt(props.getProperty("port"));
		replicaId = concatenate(address.getBytes(), numToBytes(port));
		new Thread(this::processOperations).start();
		MempoolManager.getSingleton().attachObserver(this);
	}

	private static byte[] numToBytes(long num) {
		return ByteBuffer.allocate(Long.BYTES).putLong(num).array();
	}

	private void processOperations() {
		while (true) {
			try {
				AppOperation currentOp = queuedOperations.take();
				byte[] operationResult = processOperation(currentOp.getContent());
				if (operationsWaitingResponse.remove(currentOp.getId()))
					replyOperationResults(currentOp, operationResult);
				globalOpIdx++;
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void replyOperationResults(AppOperation currentOp, byte[] operationResult) {
		ReplyListener listener = operationListeners.remove(currentOp.getId());
		Pair<byte[], Long> operationReply = Pair.of(operationResult, globalOpIdx);
		if (listener != null) listener.processReply(operationReply);
		else completedSyncOperations.put(currentOp.getId(), operationReply);
	}

	/**
	 * Locally processes an operation after it has been ordered throughout all the replicas.
	 *
	 * @param operation The generic operation to be processed.
	 * @return The output of the operation processing.
	 * <p>Should the replica the is executing this method be the one who submitted the operation,
	 * the value returned here will be the result received in the invoke operations</p>
	 */
	public abstract byte[] processOperation(byte[] operation);

	/**
	 * Submits an operation and blocks the calling thread until the operation is processed.
	 *
	 * @param operation The generic operation to be disseminated, ordered, and processed.
	 * @return A pair containing the result of the parameter operation and the global operation index of the operation's
	 * execution.
	 * <p>A value of X in the right hand side means the operation was the Xth to be processed globally.</p>
	 */
	@SneakyThrows
	public Pair<byte[], Long> invokeSyncOperation(byte @NotNull [] operation) {
		var isValid = FixedApplicationAwareValidator.getSingleton().validateReceivedOperation(operation);
		return isValid.getLeft() ? invokeValidSyncOperation(operation) : Pair.of(isValid.getRight(), -1L);
	}

	private Pair<byte[], Long> invokeValidSyncOperation(byte[] operation) throws InterruptedException {
		AppOperation content = computeAppContent(operation);
		operationsWaitingResponse.add(content.getId());
		ValueDispatcher.getSingleton().disseminateAppContentRequest(content);
		return completedSyncOperations.take(content.getId());
	}

	private AppOperation computeAppContent(byte[] operation) {
		byte[] replicaMetadata = makeMetadata();
		return new AppOperation(operation, replicaMetadata);
	}

	private byte[] makeMetadata() {
		byte[] opIdx = numToBytes(localOpIdx.getAndIncrement());
		return concatenate(replicaId, opIdx);
	}

	@SneakyThrows
	public Pair<byte[], Long> invokeLocalSyncOperation(byte @NotNull [] operation) {
		var isValid = FixedApplicationAwareValidator.getSingleton().validateReceivedOperation(operation);
		return isValid.getLeft() ? invokeValidSyncOperation(operation) : Pair.of(isValid.getRight(), -1L);
	}

	private Pair<byte[], Long> invokeValidLocalSyncOperation(byte[] operation) throws InterruptedException {
		AppOperation content = computeAppContent(operation);
		operationsWaitingResponse.add(content.getId());
		LedgerManager.getSingleton().submitOperation(content);
		return completedSyncOperations.take(content.getId());
	}

	/**
	 * Submits an operation but does not block the calling thread.
	 *
	 * @param operation The generic operation to be disseminated, ordered, and processed.
	 * @param listener  The structure that will receive and process the result of the operation.
	 */
	public void invokeAsyncOperation(byte @NotNull [] operation, @NotNull ReplyListener listener) {
		var isValid = FixedApplicationAwareValidator.getSingleton().validateReceivedOperation(operation);
		if (isValid.getLeft()) invokeValidAsyncOperation(operation, listener);
		else listener.processReply(Pair.of(isValid.getRight(), -1L));
	}

	private void invokeValidAsyncOperation(byte[] operation, ReplyListener listener) {
		AppOperation content = computeAppContent(operation);
		operationsWaitingResponse.add(content.getId());
		operationListeners.put(content.getId(), listener);
		ValueDispatcher.getSingleton().disseminateAppContentRequest(content);
	}

	public void invokeLocalAsyncOperation(byte @NotNull [] operation, @NotNull ReplyListener listener) {
		var isValid = FixedApplicationAwareValidator.getSingleton().validateReceivedOperation(operation);
		if (isValid.getLeft()) invokeValidAsyncOperation(operation, listener);
		else listener.processReply(Pair.of(isValid.getRight(), -1L));
	}

	private void invokeValidLocalAsyncOperation(byte[] operation, ReplyListener listener) {
		AppOperation content = computeAppContent(operation);
		operationsWaitingResponse.add(content.getId());
		operationListeners.put(content.getId(), listener);
		LedgerManager.getSingleton().submitOperation(content);
	}

	@Override
	public void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
		notifyNonFinalizedBlock(block);
	}

	/**
	 * Delivers a block to the application when it is received by this replica.
	 * <p>The default behavior for this operation is doing nothing,
	 * given that the application should be independent from the Blockmess logic.</p>
	 * <p>Nevertheless, this operation may be useful to extract some metrics.</p>
	 * <p>In order to make use of this operation, the class extending this one must override this method.</p>
	 *
	 * @param block The block just received. It has not yet been finalized.
	 *              <p>The content in the block is still encapsulated with the metadata required for Blockmess to
	 *              process the block</p>
	 */
	public void notifyNonFinalizedBlock(BlockmessBlock block) {
	}

	@Override
	public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
		notifyFinalizedBlocks(finalized, discarded);
	}

	/**
	 * Delivers the identifiers of blocks that have been finalized.
	 * <p>The default behavior for this operation is doing nothing,
	 * given that the application should be independent from the Blockmess logic.</p>
	 * <p>Nevertheless, this operation may be useful to extract some metrics.</p>
	 * <p>In order to make use of this operation, the class extending this one must override this method.</p>
	 *
	 * @param finalized The identifiers of blocks that have been finalized.
	 *                  <p>The blocks referred by this identifiers can be reached by storing them during the
	 * @param discarded The identifiers of blocks discarded by Blockmess.
	 *                  <p>A block may be discarded if it at some point forked the longest chain in a
	 *                  {@link ledger.blockchain.Blockchain} instance.</p>
	 * @notifyNonFinalizedBlock method execution</p>
	 */
	public void notifyFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
	}

}
