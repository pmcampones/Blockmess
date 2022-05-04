package intermediateConsensus;

import catecoin.replies.BlockContentReply;
import catecoin.txs.IndexableContent;
import ledger.Ledger;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantElection.SybilElectionProof;
import sybilResistantElection.notifications.IWasElectedWithBlockNotification;
import sybilResistantElection.notifications.IWasElectedWithoutBlockNotification;
import utils.IDGenerator;
import valueDispatcher.ValueDispatcher;
import valueDispatcher.requests.DisseminateSignedBlockRequest;

import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class SingleElementCommittee<P extends SybilElectionProof,
        B extends LedgerBlock<? extends BlockContent<? extends IndexableContent>, P>>
        extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(SingleElementCommittee.class);

    public static final short ID = IDGenerator.genId();

    private final Ledger<B> ledger;

    public SingleElementCommittee(Ledger<B> ledger) throws HandlerRegistrationException {
        super(SingleElementCommittee.class.getSimpleName(), ID);
        this.ledger = ledger;
        subscribeNotification(IWasElectedWithoutBlockNotification.ID,
                (IWasElectedWithoutBlockNotification<P> notif1, short source1) -> uponIWasElectedWithoutBlockNotification());
        subscribeNotification(IWasElectedWithBlockNotification.ID,
                (IWasElectedWithBlockNotification<B> notif, short source) -> uponIWasElectedWithBlockNotification(notif));
        registerReplyHandler(BlockContentReply.ID, (BlockContentReply<B> reply, short source) -> uponBlockContentReply(reply));
    }

    @Override
    public void init(Properties properties) {}

    private void uponIWasElectedWithoutBlockNotification() {
        Set<UUID> previous = ledger.getBlockR();
        logger.info("Requesting block following: '{}'", previous.toString());
        /*sendRequest(new BlockContentRequest<P>(previous, notif.proof),
                DefaultBlockConstructor.ID);*/
    }   //TODO Trocar por um pedido sincrono

    private void uponBlockContentReply(BlockContentReply<B> reply) {
        B block = reply.getBlockProposal();
        logger.info("Received content for block {}", block.getBlockId());
        sendRequest(new DisseminateSignedBlockRequest<>(block), ValueDispatcher.ID);
    }

    private void uponIWasElectedWithBlockNotification(IWasElectedWithBlockNotification<B> notif) {
        B block = notif.getBlockProposal();
        logger.info("Received content for block {}", block.getBlockId());
        sendRequest(new DisseminateSignedBlockRequest<>(block), ValueDispatcher.ID);
    }
}
