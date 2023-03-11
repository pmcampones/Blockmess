package validators;

import applicationInterface.GlobalProperties;
import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import ledger.blocks.BlockmessBlock;
import org.apache.commons.lang3.tuple.Pair;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import sybilResistantElection.ChainSeed;
import sybilResistantElection.SybilResistantElectionProof;
import sybilResistantElection.difficultyComputers.MultiChainDifficultyComputerImp;
import utils.CryptographicUtils;
import utils.IDGenerator;
import utils.merkleTree.MerkleRoot;
import utils.merkleTree.MerkleTree;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static java.lang.Integer.parseInt;

public class DefaultApplicationObliviousValidator extends GenericProtocol implements ApplicationObliviousValidator {

	public static final short ID = IDGenerator.genId();

	private final int maxBlockSize;

	DefaultApplicationObliviousValidator() {
		super(DefaultApplicationObliviousValidator.class.getSimpleName(), ID);
		this.maxBlockSize = parseInt(GlobalProperties.getProps().getProperty("maxBlockSize", "21000"));
	}

	@Override
	public void init(Properties properties) {
	}

	@Override
	public boolean isBlockValid(BlockmessBlock block) {
		boolean isValid = isProofValid(block)
				&& FixedApplicationAwareValidator.getSingleton().validateBlockContent(block);
		notifyBlockValidity(block);
		return isValid;
	}

	private void notifyBlockValidity(BlockmessBlock block) {
		try {
			triggerNotification(new AnswerMessageValidationNotification(block.getBlockingID()));
		} catch (InnerValueIsNotBlockingBroadcast e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean isProofValid(BlockmessBlock block) {
		SybilResistantElectionProof proof = block.getProof();
		UUID destinationChain = block.getDestinationChain();
		if (proof.getChainSeeds().stream().map(Pair::getLeft).noneMatch(id -> id.equals(destinationChain)))
			return false;
		if (block.getContentList().getSerializedSize() > maxBlockSize)
			return false;
		MerkleTree randomSeed = computeRandomSeed(block);
		byte[] solution = computeSolution(randomSeed, proof.getNonce());
		return new MultiChainDifficultyComputerImp(proof.getChainSeeds().size())
				.hasEnoughLeadingZeros(solution);
	}

	private MerkleTree computeRandomSeed(BlockmessBlock block) {
		List<byte[]> randomSeedElems = new LinkedList<>();
		randomSeedElems.add(block.getProposer().getEncoded());
		UUID destinationChain = block.getDestinationChain();
		for (var pair : block.getProof().getChainSeeds()) {
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
