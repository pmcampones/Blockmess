package catecoin.validators;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import catecoin.blocks.ContentList;
import catecoin.notifications.AnswerMessageValidationNotification;
import catecoin.txs.SlimTransaction;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.StructuredValue;
import org.apache.commons.lang3.tuple.Pair;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantElection.ChainSeed;
import sybilResistantElection.SybilResistantElectionProof;
import sybilResistantElection.difficultyComputers.MultiChainDifficultyComputerImp;
import utils.CryptographicUtils;
import utils.IDGenerator;
import utils.merkleTree.MerkleRoot;
import utils.merkleTree.MerkleTree;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class BlockmessGPoETValidator extends GenericProtocol
        implements BlockValidator<BlockmessBlock<ContentList<StructuredValue<SlimTransaction>>, SybilResistantElectionProof>> {

    public static final short ID = IDGenerator.genId();

    private final Properties props;

    public BlockmessGPoETValidator(Properties props) {
        super(BlockmessGPoETValidator.class.getSimpleName(), ID);
        this.props = props;
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {}

    @Override
    public boolean isBlockValid(BlockmessBlock<ContentList<StructuredValue<SlimTransaction>>, SybilResistantElectionProof> block) {
        boolean isValid = isProofValid(block)
                && block.getContentList().hasValidSemantics();
        notifyBlockValidity(block);
        return isValid;
    }

    private void notifyBlockValidity(BlockmessBlock<ContentList<StructuredValue<SlimTransaction>>, SybilResistantElectionProof> block) {
        try {
            triggerNotification(new AnswerMessageValidationNotification(block.getBlockingID()));
        } catch (InnerValueIsNotBlockingBroadcast e) {
            e.printStackTrace();
        }
    }

    public boolean isProofValid(BlockmessBlock<ContentList<StructuredValue<SlimTransaction>>, SybilResistantElectionProof> block) {
        SybilResistantElectionProof proof = block.getSybilElectionProof();
        UUID destinationChain = block.getDestinationChain();
        if (proof.getChainSeeds().stream().map(Pair::getLeft).noneMatch(id -> id.equals(destinationChain)))
            return false;
        MerkleTree randomSeed = computeRandomSeed(block);
        byte[] solution = computeSolution(randomSeed, proof.getNonce());
        return new MultiChainDifficultyComputerImp(props, proof.getChainSeeds().size())
                .hasEnoughLeadingZeros(solution);
    }

    private MerkleTree computeRandomSeed(
            BlockmessBlock<ContentList<StructuredValue<SlimTransaction>>, SybilResistantElectionProof> block) {
        List<byte[]> randomSeedElems = new LinkedList<>();
        randomSeedElems.add(block.getProposer().getEncoded());
        UUID destinationChain = block.getDestinationChain();
        for (var pair : block.getSybilElectionProof().getChainSeeds()) {
            byte[] chainSeed = pair.getLeft().equals(destinationChain) ?
                    ChainSeed.computeChainSeed(block.getDestinationChain(),
                            block.getContentList(), block.getPrevRefs().get(0)) :
                    pair.getRight();
            randomSeedElems.add(chainSeed);
        }
        return new MerkleRoot(randomSeedElems);
    }

    private byte[] computeSolution(MerkleTree randomSeed, int nonce) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(randomSeed.getHashValue());
        byteBuffer.putInt(nonce);
        return CryptographicUtils.hashInput(byteBuffer.array());
    }

}
