package catecoin.blockConstructors;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import catecoin.notifications.AnswerMessageValidationNotification;
import catecoin.notifications.DeliverFinalizedBlocksContentNotification;
import catecoin.notifications.DeliverIndexableContentNotification;
import catecoin.replies.BlockContentReply;
import catecoin.requests.BlockContentRequest;
import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.SybilElectionProof;
import utils.IDGenerator;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.*;

public class BabelBlockDirector<E extends IndexableContent, C extends BlockContent<E>,
        B extends LedgerBlock<C, P>, P extends SybilElectionProof>
        extends GenericProtocol implements BlockDirector<E,C,B,P> {

    private static final Logger logger = LogManager.getLogger(BabelBlockDirector.class);

    private final BlockDirector<E,C,B,P> inner;

    public BabelBlockDirector(BlockDirector<E,C,B,P> inner)
            throws HandlerRegistrationException {
        super(BabelBlockDirector.class.getSimpleName(), IDGenerator.genId());
        this.inner = inner;
        subscribeNotification(DeliverIndexableContentNotification.ID, (DeliverIndexableContentNotification<E> notif1, short source1) -> uponDeliverTransactionNotification(notif1));
        subscribeNotification(DeliverFinalizedBlocksContentNotification.ID, (DeliverFinalizedBlocksContentNotification notif, short source) -> uponDeliverFinalizedBlocksContentNotification(notif));
        registerRequestHandler(BlockContentRequest.ID, this::uponBlockContentRequest);
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {}

    @Override
    public B createBlockProposal(Set<UUID> previousStates, P proof) throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        return inner.createBlockProposal(previousStates, proof);
    }

    @Override
    public B createBoundBlockProposal(Set<UUID> previousStates, P proof, int maxTxs) throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        return inner.createBoundBlockProposal(previousStates, proof, maxTxs);
    }

    @Override
    public C createBlockContent(Set<UUID> previousStates, int usedSpace) throws IOException {
        return inner.createBlockContent(previousStates, usedSpace);
    }

    @Override
    public List<E> generateBlockContentList(Collection<UUID> states, int usedSpace) throws IOException {
        return inner.generateBlockContentList(states, usedSpace);
    }

    @Override
    public List<E> generateBoundBlockContentList(Collection<UUID> states, int usedSpace, int maxTxs)
            throws IOException {
        return inner.generateBoundBlockContentList(states, usedSpace, maxTxs);
    }

    @Override
    public void submitContent(Collection<E> content) {
        inner.submitContent(content);
    }

    @Override
    public void submitContent(E content) {
        inner.submitContent(content);
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        inner.deleteContent(contentIds);
    }

    @Override
    public Collection<E> getStoredContent() {
        return null;
    }

    @Override
    public void halveChainThroughput() {
        inner.halveChainThroughput();
    }

    @Override
    public void doubleChainThroughput() {
        inner.doubleChainThroughput();
    }

    @Override
    public int getThroughputReduction() {
        return inner.getThroughputReduction();
    }

    @Override
    public void setChainThroughputReduction(int reduction) {
        inner.setChainThroughputReduction(reduction);
    }

    private void uponDeliverTransactionNotification(DeliverIndexableContentNotification<E> notif) {
        E content = notif.getContent();
        boolean isValid = content.hasValidSemantics();
        if (isValid) {
            //logger.info("Received transaction from '{}' to '{}'", content.getOrigin(), content.getDestination());
            submitContent(content);
        } else {
            System.out.println("Received invalid content somehow.");
        }
        if (content.isBlocking()) {
            try {
                triggerNotification(new AnswerMessageValidationNotification(isValid, content.getBlockingID()));
            } catch (InnerValueIsNotBlockingBroadcast innerValueIsNotBlockingBroadcast) {
                innerValueIsNotBlockingBroadcast.printStackTrace();
            }
        }
    }

    private void uponDeliverFinalizedBlocksContentNotification(
            DeliverFinalizedBlocksContentNotification notif) {
        deleteContent(notif.getUsedTxs());
    }

    private void uponBlockContentRequest(BlockContentRequest<P> req, short source) {
        try {
            B block = createBlockProposal(req.getPreviousStates(), req.getSybilResistantProof());
            sendReply(new BlockContentReply<>(block), source);
        } catch (Exception e) {
            logger.error("Unable to generate transaction because: {}", e.getMessage());
        }
    }
}
