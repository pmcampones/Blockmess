package ledger.blocks;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import catecoin.blocks.ValidatorSignature;
import io.netty.buffer.ByteBuf;
import main.CryptographicUtils;
import main.ProtoPojo;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantElection.SybilElectionProof;

import java.io.IOException;
import java.security.*;
import java.util.List;
import java.util.UUID;

public class BlockmessBlockImp<C extends BlockContent<? extends ProtoPojo>, P extends SybilElectionProof>
        implements BlockmessBlock<C,P> {

    public static final short ID = 11037;

    private final LedgerBlock<C,P> ledgerBlock;

    private final UUID destinationChain;

    private final long currentRank;

    private final long nextRank;

    public BlockmessBlockImp(int inherentWeight, List<UUID> prevRefs, C blockContent,
                             P proof, KeyPair proposer, UUID destinationChain, long currentRank, long nextRank)
            throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, IOException {
        UUID blockId = computeBlockId(inherentWeight, prevRefs, blockContent, proof, destinationChain);
        this.ledgerBlock = new LedgerBlockImp<>(blockId,inherentWeight, prevRefs, blockContent, proof, proposer, ID);
        this.destinationChain = destinationChain;
        this.currentRank = currentRank;
        this.nextRank = nextRank;
    }

    private BlockmessBlockImp(int inherentWeight, List<UUID> prevRefs, C blockContent,
                              P proof, List<ValidatorSignature> validatorSignatures, UUID destinationChain,
                              long currentRank, long nextRank)
            throws IOException {
        UUID blockId = computeBlockId(inherentWeight, prevRefs, blockContent, proof, destinationChain);
        this.ledgerBlock =
                new LedgerBlockImp<>(blockId, inherentWeight, prevRefs, blockContent, proof, validatorSignatures, ID);
        this.destinationChain = destinationChain;
        this.currentRank = currentRank;
        this.nextRank = nextRank;
    }

    private UUID computeBlockId(int inherentWeight, List<UUID> prevRefs, C blockContent,
                                P proof, UUID destinationChain) throws IOException {
        byte[] blockBytes = computeBlockBytes(inherentWeight, prevRefs, blockContent, proof, destinationChain);
        return CryptographicUtils.generateUUIDFromBytes(blockBytes);
    }

    private byte[] computeBlockBytes(int inherentWeight, List<UUID> prevRefs, C blockContent,
                                     P proof, UUID destinationChain) throws IOException {
        int bufferSize = Integer.BYTES
                + prevRefs.size() * 2 * Long.BYTES
                + blockContent.getSerializedSize()
                + proof.getSerializedSize()
                + 2 * Long.BYTES;
        ByteBuf in = LedgerBlockImp.getLedgerBlockByteBuf(bufferSize, inherentWeight, prevRefs, blockContent, proof);
        in.writeLong(destinationChain.getMostSignificantBits());
        in.writeLong(destinationChain.getLeastSignificantBits());
        return in.array();
    }


    @Override
    public UUID getDestinationChain() {
        return this.destinationChain;
    }

    @Override
    public long getBlockRank() {
        return currentRank;
    }

    @Override
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
    public C getBlockContent() {
        return ledgerBlock.getBlockContent();
    }

    @Override
    public P getSybilElectionProof() {
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
    public boolean isBlocking() {
        return true;
    }

    @Override
    public UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast {
        return ledgerBlock.getBlockingID();
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo protoPojo, ByteBuf out) throws IOException {
            BlockmessBlockImp block = (BlockmessBlockImp) protoPojo;
            out.writeInt(block.getInherentWeight());
            ProtoPojo.serializeUuids(block.getPrevRefs(), out);
            serializePojo(block.getBlockContent(), out);
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
            BlockContent blockContent = (BlockContent) deserializePojo(in);
            SybilElectionProof proof = (SybilElectionProof) deserializePojo(in);
            List<ValidatorSignature> validatorSignatures = LedgerBlockImp.deserializeValidatorSignatures(in);
            UUID destinationChain = deserializeDestinationChain(in);
            long currentRank = in.readLong();
            long nextRank = in.readLong();
            return new BlockmessBlockImp(inherentWeight, prevRefs, blockContent,
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

}
