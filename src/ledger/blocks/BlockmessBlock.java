package ledger.blocks;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import catecoin.blocks.ContentList;
import catecoin.blocks.ValidatorSignature;
import catecoin.txs.Transaction;
import io.netty.buffer.ByteBuf;
import ledger.ledgerManager.StructuredValue;
import main.ProtoPojo;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantElection.SybilResistantElectionProof;
import utils.CryptographicUtils;

import java.io.IOException;
import java.security.*;
import java.util.List;
import java.util.UUID;

public class BlockmessBlock
        implements LedgerBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof> {

    public static final short ID = 11037;
    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo protoPojo, ByteBuf out) throws IOException {
            BlockmessBlock block = (BlockmessBlock) protoPojo;
            out.writeInt(block.getInherentWeight());
            ProtoPojo.serializeUuids(block.getPrevRefs(), out);
            serializePojo(block.getContentList(), out);
            serializePojo(block.getSybilElectionProof(), out);
            serializeValidatorSignatures(block.getSignatures(), out);
            serializeDestinationChain(block.destinationChain, out);
            out.writeLong(block.currentRank);
            out.writeLong(block.nextRank);
        }

        private void serializePojo(ProtoPojo pojo, ByteBuf out) throws IOException {
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
         * be different than the content serialized, and the exception is triggered.
         */
        @Override
        public ProtoPojo deserialize(ByteBuf in) throws IOException {
            int inherentWeight = in.readInt();
            List<UUID> prevRefs = ProtoPojo.deserializeUuids(in);
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

        private ProtoPojo deserializePojo(ByteBuf in) throws IOException {
            short pojoId = in.readShort();
            ISerializer<ProtoPojo> serializer = ProtoPojo.pojoSerializers.get(pojoId);
            return serializer.deserialize(in);
        }

    };
    private final LedgerBlock<ContentList<StructuredValue<Transaction>>,SybilResistantElectionProof> ledgerBlock;
    private final UUID destinationChain;
    private final long currentRank;
    private final long nextRank;

    public BlockmessBlock(int inherentWeight, List<UUID> prevRefs, ContentList<StructuredValue<Transaction>> contentList,
                          SybilResistantElectionProof proof, KeyPair proposer, UUID destinationChain, long currentRank, long nextRank)
            throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, IOException {
        UUID blockId = computeBlockId(inherentWeight, prevRefs, contentList, proof, destinationChain);
        this.ledgerBlock = new LedgerBlockImp<>(blockId,inherentWeight, prevRefs, contentList, proof, proposer, ID);
        this.destinationChain = destinationChain;
        this.currentRank = currentRank;
        this.nextRank = nextRank;
    }

    private UUID computeBlockId(int inherentWeight, List<UUID> prevRefs, ContentList<StructuredValue<Transaction>> contentList,
                                SybilResistantElectionProof proof, UUID destinationChain) throws IOException {
        byte[] blockBytes = computeBlockBytes(inherentWeight, prevRefs, contentList, proof, destinationChain);
        return CryptographicUtils.generateUUIDFromBytes(blockBytes);
    }

    private byte[] computeBlockBytes(int inherentWeight, List<UUID> prevRefs, ContentList<StructuredValue<Transaction>> ContentList,
                                     SybilResistantElectionProof proof, UUID destinationChain) throws IOException {
        int bufferSize = Integer.BYTES
                + prevRefs.size() * 2 * Long.BYTES
                + ContentList.getSerializedSize()
                + proof.getSerializedSize()
                + 2 * Long.BYTES;
        ByteBuf in = LedgerBlockImp.getLedgerBlockByteBuf(bufferSize, inherentWeight, prevRefs, ContentList, proof);
        in.writeLong(destinationChain.getMostSignificantBits());
        in.writeLong(destinationChain.getLeastSignificantBits());
        return in.array();
    }


    private BlockmessBlock(int inherentWeight, List<UUID> prevRefs, ContentList<StructuredValue<Transaction>> contentList,
                           SybilResistantElectionProof proof, List<ValidatorSignature> validatorSignatures, UUID destinationChain,
                           long currentRank, long nextRank)
            throws IOException {
        UUID blockId = computeBlockId(inherentWeight, prevRefs, contentList, proof, destinationChain);
        this.ledgerBlock =
                new LedgerBlockImp<>(blockId, inherentWeight, prevRefs, contentList, proof, validatorSignatures, ID);
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
    public UUID getBlockId() {
        return ledgerBlock.getBlockId();
    }

    @Override
    public int getInherentWeight() {
        return ledgerBlock.getInherentWeight();
    }

    @Override
    public List<UUID> getPrevRefs() {
        return ledgerBlock.getPrevRefs();
    }

    @Override
    public ContentList<StructuredValue<Transaction>> getContentList() {
        return ledgerBlock.getContentList();
    }

    @Override
    public SybilResistantElectionProof getSybilElectionProof() {
        return ledgerBlock.getSybilElectionProof();
    }

    @Override
    public List<ValidatorSignature> getSignatures() {
        return ledgerBlock.getSignatures();
    }

    @Override
    public void addValidatorSignature(ValidatorSignature validatorSignature) {
        ledgerBlock.addValidatorSignature(validatorSignature);
    }

    @Override
    public PublicKey getProposer() {
        return ledgerBlock.getProposer();
    }

    @Override
    public boolean hasValidSemantics() {
        return ledgerBlock.hasValidSemantics();
    }

    @Override
    public int getSerializedSize() throws IOException {
        return ledgerBlock.getSerializedSize();
    }

    @Override
    public short getClassId() {
        return ID;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast {
        return ledgerBlock.getBlockingID();
    }

}
