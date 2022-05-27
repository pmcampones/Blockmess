package ledger.blocks;

import broadcastProtocols.BroadcastValue;
import io.netty.buffer.ByteBuf;
import lombok.experimental.Delegate;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantElection.SybilResistantElectionProof;
import utils.CryptographicUtils;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.List;
import java.util.UUID;

public class BlockmessBlock implements LedgerBlock {

	public static final short ID = 11037;
	public static final ISerializer<BroadcastValue> serializer = new ISerializer<>() {

		@Override
		public void serialize(BroadcastValue broadcastValue, ByteBuf out) throws IOException {
			BlockmessBlock block = (BlockmessBlock) broadcastValue;
			out.writeInt(block.getInherentWeight());
			BroadcastValue.serializeUuids(block.getPrevRefs(), out);
			serializePojo(block.getContentList(), out);
			serializePojo(block.getProof(), out);
			serializeValidatorSignatures(block.getSignatures(), out);
			serializeDestinationChain(block.destinationChain, out);
			out.writeLong(block.currentRank);
			out.writeLong(block.nextRank);
		}

		private void serializePojo(BroadcastValue pojo, ByteBuf out) throws IOException {
			out.writeShort(pojo.getClassId());
			pojo.getSerializer().serialize(pojo, out);
		}

		private void serializeValidatorSignatures(List<ValidatorSignature> validatorSignatures, ByteBuf out) {
			out.writeShort(validatorSignatures.size());
			for (ValidatorSignature validatorSignature : validatorSignatures) {
				byte[] validator = validatorSignature.getValidatorKey().getEncoded();
				out.writeShort(validator.length);
				out.writeBytes(validator);
				byte[] signature = validatorSignature.getValidatorSignature();
				out.writeShort(signature.length);
				out.writeBytes(signature);
			}
		}

		private void serializeDestinationChain(UUID destinationChain, ByteBuf out) {
			out.writeLong(destinationChain.getMostSignificantBits());
			out.writeLong(destinationChain.getLeastSignificantBits());
		}

		/**
		 * First lines copied from the deserializer in LedgerBlockImp because I could not extract that logic.
		 * <p>Beware of certain bugs if the code in the serializer of LedgerBlockImp is altered.</p>
		 * @throws IOException When the serializer in {@link LedgerBlockImp} is modified,
		 * in particular the serialize method, the content being deserialized here may
		 * be different from the content serialized, and the exception is triggered.
		 */
		@Override
		public BroadcastValue deserialize(ByteBuf in) throws IOException {
			int inherentWeight = in.readInt();
			List<UUID> prevRefs = BroadcastValue.deserializeUuids(in);
			ContentList contentList = (ContentList) deserializePojo(in);
			SybilResistantElectionProof proof = (SybilResistantElectionProof) deserializePojo(in);
			List<ValidatorSignature> validatorSignatures = LedgerBlockImp.deserializeValidatorSignatures(in);
			UUID destinationChain = deserializeDestinationChain(in);
			long currentRank = in.readLong();
			long nextRank = in.readLong();
			return new BlockmessBlock(inherentWeight, prevRefs, contentList,
					proof, validatorSignatures, destinationChain, currentRank, nextRank);
		}

		private UUID deserializeDestinationChain(ByteBuf in) {
			return new UUID(in.readLong(), in.readLong());
		}

		private BroadcastValue deserializePojo(ByteBuf in) throws IOException {
			short pojoId = in.readShort();
			ISerializer<BroadcastValue> serializer = BroadcastValue.pojoSerializers.get(pojoId);
			return serializer.deserialize(in);
		}

	};

	@Delegate(excludes = ExcludeInnerLedger.class)
	private final LedgerBlock ledgerBlock;
	private final UUID destinationChain;
	private final long currentRank;
	private final long nextRank;

	public BlockmessBlock(int inherentWeight, List<UUID> prevRefs, ContentList contentList,
						  SybilResistantElectionProof proof, KeyPair proposer, UUID destinationChain, long currentRank, long nextRank)
			throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, IOException {
		UUID blockId = computeBlockId(inherentWeight, prevRefs, contentList, proof, destinationChain);
		this.ledgerBlock = new LedgerBlockImp(blockId, inherentWeight, prevRefs, contentList, proof, proposer, ID);
		this.destinationChain = destinationChain;
		this.currentRank = currentRank;
		this.nextRank = nextRank;
	}

	private UUID computeBlockId(int inherentWeight, List<UUID> prevRefs, ContentList contentList,
								SybilResistantElectionProof proof, UUID destinationChain) throws IOException {
		byte[] blockBytes = computeBlockBytes(inherentWeight, prevRefs, contentList, proof, destinationChain);
		return CryptographicUtils.generateUUIDFromBytes(blockBytes);
	}

	private byte[] computeBlockBytes(int inherentWeight, List<UUID> prevRefs, ContentList contentList,
									 SybilResistantElectionProof proof, UUID destinationChain) throws IOException {
		int bufferSize = Integer.BYTES
				+ prevRefs.size() * 2 * Long.BYTES
				+ contentList.getSerializedSize()
				+ proof.getSerializedSize()
				+ 2 * Long.BYTES;
		ByteBuf in = LedgerBlockImp.getLedgerBlockByteBuf(bufferSize, inherentWeight, prevRefs, contentList, proof);
		in.writeLong(destinationChain.getMostSignificantBits());
		in.writeLong(destinationChain.getLeastSignificantBits());
		return in.array();
	}


	private BlockmessBlock(int inherentWeight, List<UUID> prevRefs, ContentList contentList,
						   SybilResistantElectionProof proof, List<ValidatorSignature> validatorSignatures, UUID destinationChain,
						   long currentRank, long nextRank)
			throws IOException {
		UUID blockId = computeBlockId(inherentWeight, prevRefs, contentList, proof, destinationChain);
		this.ledgerBlock = new LedgerBlockImp(blockId, inherentWeight, prevRefs, contentList, proof, validatorSignatures, ID);
		this.destinationChain = destinationChain;
		this.currentRank = currentRank;
		this.nextRank = nextRank;
	}

	public UUID getDestinationChain() {
		return this.destinationChain;
	}

	public long getBlockRank() {
		return currentRank;
	}

	public long getNextRank() {
		return nextRank;
	}

	@Override
	public short getClassId() {
		return ID;
	}

	@Override
	public ISerializer<BroadcastValue> getSerializer() {
		return serializer;
	}

	@Override
	public boolean isBlocking() {
		return true;
	}

	private interface ExcludeInnerLedger {
		short getClassId();

		ISerializer<BroadcastValue> getSerializer();

		boolean isBlocking();
	}


}
