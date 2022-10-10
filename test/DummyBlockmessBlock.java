import broadcastProtocols.BroadcastValue;
import ledger.blocks.BlockmessBlock;
import ledger.blocks.ContentList;
import ledger.blocks.ValidatorSignature;
import lombok.AllArgsConstructor;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantElection.SybilResistantElectionProof;

import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
public class DummyBlockmessBlock implements BlockmessBlock {

	private final UUID prev;

	@Override
	public UUID getBlockId() {
		return null;
	}

	@Override
	public int getInherentWeight() {
		return 0;
	}

	@Override
	public List<UUID> getPrevRefs() {
		return List.of(prev);
	}

	@Override
	public ContentList getContentList() {
		return null;
	}

	@Override
	public SybilResistantElectionProof getProof() {
		return null;
	}

	@Override
	public List<ValidatorSignature> getSignatures() {
		return Collections.emptyList();
	}

	@Override
	public void addValidatorSignature(ValidatorSignature validatorSignature) {
	}

	@Override
	public PublicKey getProposer() {
		return null;
	}

	@Override
	public boolean hasValidSemantics() {
		return true;
	}

	@Override
	public int getSerializedSize() {
		return 0;
	}

	@Override
	public UUID getDestinationChain() {
		return null;
	}

	@Override
	public long getBlockRank() {
		return 0;
	}

	@Override
	public long getNextRank() {
		return 0;
	}

	@Override
	public short getClassId() {
		return 0;
	}

	@Override
	public ISerializer<BroadcastValue> getSerializer() {
		return null;
	}

	@Override
	public boolean isBlocking() {
		return false;
	}

	@Override
	public UUID getBlockingID() {
		return null;
	}
}
