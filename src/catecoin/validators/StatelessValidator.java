package catecoin.validators;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import catecoin.blocks.ContentList;
import catecoin.notifications.AnswerMessageValidationNotification;
import catecoin.txs.SlimTransaction;
import ledger.blocks.LedgerBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantElection.SybilResistantElectionProof;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

import static catecoin.blockConstructors.BaseContentStorage.MAX_BLOCK_SIZE;
import static java.lang.Integer.parseInt;

/**
 * Block validator that does not verify if the inputs and outputs match.
 * Effectively only makes the necessary validations that do not require maintaining a state.
 */
public class StatelessValidator<P extends SybilResistantElectionProof>
        extends GenericProtocol implements BlockValidator<LedgerBlock<ContentList<SlimTransaction>, P>> {

    private static final Logger logger = LogManager.getLogger(StatelessValidator.class);

    protected final SybilProofValidator<P> proofValidator;

    private final int maxBlockSize;

    protected StatelessValidator(Properties props, String protoName, short protoId, SybilProofValidator<P> proofValidator)  {
        super(protoName, protoId);
        this.proofValidator = proofValidator;
        this.maxBlockSize = parseInt(props.getProperty("maxBlockSize",
                String.valueOf(MAX_BLOCK_SIZE)));
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {}

    @Override
    public boolean isBlockValid(LedgerBlock<ContentList<SlimTransaction>,P> block) {
        boolean isValid = receivedValid(block);
        notifyOfBlockValidity(block, isValid);
        return isValid;
    }

    private void notifyOfBlockValidity(LedgerBlock<ContentList<SlimTransaction>,P> block, boolean isValid) {
        try {
            tryToNotifyOfBlockValidity(block, isValid);
        } catch (InnerValueIsNotBlockingBroadcast innerValueIsNotBlockingBroadcast) {
            logger.error("Attempted to notify the block's validity, but somehow it was not a BlockingBroadcast.\n" +
                    "The block's id: " + block.getBlockId());
        }
    }

    private void tryToNotifyOfBlockValidity(LedgerBlock<ContentList<SlimTransaction>,P> block, boolean isValid)
            throws InnerValueIsNotBlockingBroadcast {
        UUID blockingId = block.getBlockId();
        triggerNotification(new AnswerMessageValidationNotification(blockingId));
        logger.debug("Notified the broadcast protocol that the block submited {} has validity: {}",
                blockingId, isValid);
    }

    public boolean receivedValid(LedgerBlock<ContentList<SlimTransaction>,P> block) {
        return hasValidSize(block)
                && proofValidator.isValid(block.getSybilElectionProof(), block.getProposer())
                && block.getContentList().hasValidSemantics()
                && block.getSignatures().size() == 1
                && block.getSignatures().get(0).isValid(block.getBlockId());
    }

    boolean hasValidSize(LedgerBlock<ContentList<SlimTransaction>,P> block) {
        try {
            return block.getSerializedSize() <= maxBlockSize;
        } catch (IOException e) {
            logger.info("Could not compute the block serialized size because of exception: '{}'",
                    e.getMessage());
        }
        return false;
    }

}
