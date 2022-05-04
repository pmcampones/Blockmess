package ledger;

import catecoin.notifications.DeliverFinalizedBlockIdentifiersNotification;
import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import ledger.blocks.LedgerBlockImp;
import ledger.notifications.DeliverNonFinalizedBlockNotification;
import main.ProtoPojo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantElection.SybilElectionProof;
import utils.IDGenerator;
import valueDispatcher.notifications.DeliverSignedBlockNotification;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class BabelLedger<B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>,? extends SybilElectionProof>>
        extends GenericProtocol
        implements LedgerObserver<B> {

    private static final Logger logger = LogManager.getLogger(BabelLedger.class);

    public static final short ID = IDGenerator.genId();

    /**
     * This Ledger is simultaneously a strategy to handle block submission according to the Strategy pattern,
     * as well as a subject to listen to changes in the Observer pattern.
     */
    private final Ledger<B> ledger;

    public BabelLedger(Ledger<B> ledger) throws HandlerRegistrationException {
        super(BabelLedger.class.getSimpleName(), ID);
        this.ledger = attachToSubjectLedger(ledger);
        subscribeNotification(DeliverSignedBlockNotification.ID, (DeliverSignedBlockNotification<B> notif, short id) -> uponDeliverSignedBlockNotification(notif));
        ProtoPojo.pojoSerializers.put(LedgerBlockImp.ID, LedgerBlockImp.serializer);
    }

    private Ledger<B> attachToSubjectLedger(Ledger<B> ledger) {
        ledger.attachObserver(this);
        return ledger;
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {}

    private void uponDeliverSignedBlockNotification (DeliverSignedBlockNotification<B> notif) {
        B block = notif.getBlock();
        logger.info("Ledger received block: {}", block.getBlockId());
        ledger.submitBlock(block);
    }

    @Override
    public void deliverNonFinalizedBlock(B block, int weight) {
        triggerNotification(new DeliverNonFinalizedBlockNotification<>(block, weight));
    }

    @Override
    public void deliverFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {
        if (!finalized.isEmpty() || !discarded.isEmpty())
            triggerNotification(new DeliverFinalizedBlockIdentifiersNotification(discarded, finalized));
    }
}
