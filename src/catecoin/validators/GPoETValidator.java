package catecoin.validators;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import catecoin.notifications.AnswerMessageValidationNotification;
import catecoin.txs.SlimTransaction;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.poet.gpoet.GPoET;
import sybilResistantCommitteeElection.poet.gpoet.GPoETProof;
import sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers.LedgerGPoETDifficultyComputer;
import utils.IDGenerator;
import utils.merkleTree.MerkleRoot;
import utils.merkleTree.MerkleTree;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

public class GPoETValidator extends GenericProtocol implements BlockValidator<LedgerBlock<BlockContent<SlimTransaction>, GPoETProof>> {

    public static final short ID = IDGenerator.genId();

    private final LedgerGPoETDifficultyComputer difficultyComputer;

    public GPoETValidator(LedgerGPoETDifficultyComputer difficultyComputer) {
        super(GPoETValidator.class.getSimpleName(), ID);
        this.difficultyComputer = difficultyComputer;
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {}

    @Override
    public boolean isBlockValid(LedgerBlock<BlockContent<SlimTransaction>, GPoETProof> block) {
        boolean isValid = isProofValid(block.getSybilElectionProof(), block.getPrevRefs(), block.getBlockContent(), block.getProposer())
                && block.getBlockContent().hasValidSemantics();
        notifyBlockValidity(block);
        return isValid;
    }

    private void notifyBlockValidity(LedgerBlock<BlockContent<SlimTransaction>, GPoETProof> block) {
        try {
            triggerNotification(new AnswerMessageValidationNotification(block.getBlockingID()));
        } catch (InnerValueIsNotBlockingBroadcast e) {
            e.printStackTrace();
        }
    }

    public boolean isProofValid(GPoETProof proof, List<UUID> prevRef, BlockContent<?> blockContent, PublicKey proposer) {
        return isProofValid(proof, prevRef, blockContent, proposer, difficultyComputer);
    }

    public static boolean isProofValid(GPoETProof proof, List<UUID> prevRef, BlockContent<?> blockContent,
                               PublicKey proposer, LedgerGPoETDifficultyComputer difficultyComputer) {
        MerkleTree randomSeed = getBlockRandomSeed(prevRef, blockContent, proposer);
        byte[] solution = GPoET.computeSolution(randomSeed, proof.getNonce());
        return difficultyComputer.hasEnoughLeadingZeros(solution);
    }


    private static MerkleTree getBlockRandomSeed(List<UUID> prevRef, BlockContent<?> blockContent, PublicKey proposer) {
        List<byte[]> leaves = new ArrayList<>();
        leaves.add(proposer.getEncoded());
        leaves.addAll(prevRef.stream().map(GPoETValidator::convertIdToBytes).collect(Collectors.toList()));
        leaves.add(blockContent.getContentHash());
        return new MerkleRoot(leaves);
    }

    private static byte[] convertIdToBytes(UUID id) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(2 * Long.BYTES);
        byteBuffer.putLong(id.getMostSignificantBits());
        byteBuffer.putLong(id.getLeastSignificantBits());
        return byteBuffer.array();
    }
}
