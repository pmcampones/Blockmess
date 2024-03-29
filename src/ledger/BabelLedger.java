package ledger;

import broadcastProtocols.BroadcastValue;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.LedgerBlockImp;
import ledger.notifications.DeliverNonFinalizedBlockNotification;
import mempoolManager.notifications.DeliverFinalizedBlockIdentifiersNotification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import utils.IDGenerator;
import valueDispatcher.notifications.DeliverSignedBlockNotification;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class BabelLedger extends GenericProtocol implements LedgerObserver {

	public static final short ID = IDGenerator.genId();
	private static final Logger logger = LogManager.getLogger(BabelLedger.class);
	/**
	 * This Ledger is simultaneously a strategy to handle block submission according to the Strategy pattern, as well as
	 * a subject to listen to changes in the Observer pattern.
	 */
	private final Ledger ledger;

	public BabelLedger(Ledger ledger) throws HandlerRegistrationException {
		super(BabelLedger.class.getSimpleName(), ID);
		this.ledger = attachToSubjectLedger(ledger);
		subscribeNotification(DeliverSignedBlockNotification.ID, (DeliverSignedBlockNotification<BlockmessBlock> notif, short id) -> uponDeliverSignedBlockNotification(notif));
		BroadcastValue.pojoSerializers.put(LedgerBlockImp.ID, LedgerBlockImp.serializer);
	}

	private Ledger attachToSubjectLedger(Ledger ledger) {
		ledger.attachObserver(this);
		return ledger;
	}

	private void uponDeliverSignedBlockNotification(DeliverSignedBlockNotification<BlockmessBlock> notif) {
		BlockmessBlock block = notif.getBlock();
		logger.info("Ledger received block: {}", block.getBlockId());
		ledger.submitBlock(block);
	}

	@Override
	public void init(Properties properties) {
	}

	@Override
	public void deliverNonFinalizedBlock(BlockmessBlock block, int weight) {
		triggerNotification(new DeliverNonFinalizedBlockNotification<>(block, weight));
	}

	@Override
	public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
		if (!finalized.isEmpty() || !discarded.isEmpty())
			triggerNotification(new DeliverFinalizedBlockIdentifiersNotification(discarded, finalized));
	}
}
