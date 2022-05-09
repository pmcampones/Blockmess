package ledger.blocks;

import broadcastProtocols.BroadcastValue;
import broadcastProtocols.BroadcastValueAbstract;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantElection.SybilResistantElectionProof;
import utils.CryptographicUtils;

import java.io.IOException;
import java.security.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LedgerBlockImp extends BroadcastValueAbstract implements LedgerBlock {

    public static final short ID = 9888;

    private final UUID blockId;

    private final int inherentWeight;

    private final List<UUID> prevRefs;

    public static final ISerializer<BroadcastValue> serializer = new ISerializer<>() {

        @Override
        public void serialize(BroadcastValue broadcastValue, ByteBuf out) throws IOException {
            LedgerBlock block = (LedgerBlock) broadcastValue;
            out.writeInt(block.getInherentWeight());
            serializePrevs(block.getPrevRefs(), out);
            serializePojo(block.getContentList(), out);
            serializePojo(block.getSybilElectionProof(), out);
            serializeValidatorSignatures(block.getSignatures(), out);
        }

        private void serializePrevs(List<UUID> prevs, ByteBuf out) {
            out.writeShort(prevs.size());
            for (UUID id : prevs) {
                out.writeLong(id.getMostSignificantBits());
                out.writeLong(id.getLeastSignificantBits());
            }
        }

        private void serializePojo(BroadcastValue pojo, ByteBuf out) throws IOException {
            out.writeShort(pojo.getClassId());
            ISerializer<BroadcastValue> serializer = pojo.getSerializer();
            serializer.serialize(pojo, out);
        }

        private void serializeValidatorSignatures(List<ValidatorSignature> validatorSignatures, ByteBuf out)
                throws IOException {
            out.writeShort(validatorSignatures.size());
            ISerializer<ValidatorSignature> serializer = ValidatorSignature.serializer;
            for (ValidatorSignature validatorSignature : validatorSignatures)
                serializer.serialize(validatorSignature, out);
        }

        @Override
        public BroadcastValue deserialize(ByteBuf in) throws IOException {
            int inherentWeight = in.readInt();
            List<UUID> prevRefs = BroadcastValue.deserializeUuids(in);
            ContentList contentList = (ContentList) deserializeInnerPojo(in);
            SybilResistantElectionProof proof = (SybilResistantElectionProof) deserializeInnerPojo(in);
            List<ValidatorSignature> validatorSignatures = deserializeValidatorSignatures(in);
            return new LedgerBlockImp(inherentWeight, prevRefs,
                    contentList, proof, validatorSignatures);
        }

    };

    private final SybilResistantElectionProof proof;
    private final ContentList contentList;
    private final List<ValidatorSignature> validatorSignatures;

    /**
     * This constructor is meant to be used by nodes receiving the Block and called during the deserialization.
     */
    private LedgerBlockImp(int inherentWeight, List<UUID> prevRefs, ContentList contentList,
                           SybilResistantElectionProof proof, List<ValidatorSignature> validatorSignatures) throws IOException {
        super(ID);
        this.inherentWeight = inherentWeight;
        this.prevRefs = prevRefs;
        this.contentList = contentList;
        this.proof = proof;
        this.blockId = computeBlockId();
        this.validatorSignatures = validatorSignatures;
    }

    /**
     * This constructor is used when we do not want to compute the blockId and instead wish to receive its value.
     * <p>An example of this is when a block class extends this and the computation of the blockId changes because
     * of some extra parameters.</p>
     */
    LedgerBlockImp(UUID blockId, int inherentWeight, List<UUID> prevRefs, ContentList contentList,
                   SybilResistantElectionProof proof, KeyPair proposer, short classId)
            throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        super(classId);
        this.blockId = blockId;
        this.inherentWeight = inherentWeight;
        this.prevRefs = prevRefs;
        this.contentList = contentList;
        this.proof = proof;
        this.validatorSignatures = genValidatorSignaturesFromProposer(blockId, proposer);
    }

    LedgerBlockImp(UUID blockId, int inherentWeight, List<UUID> prevRefs, ContentList contentList,
                   SybilResistantElectionProof proof, List<ValidatorSignature> validatorSignatures, short classId) {
        super(classId);
        this.blockId = blockId;
        this.inherentWeight = inherentWeight;
        this.prevRefs = prevRefs;
        this.contentList = contentList;
        this.proof = proof;
        this.validatorSignatures = validatorSignatures;
    }

    private UUID computeBlockId() throws IOException {
        byte[] blockBytes = computeBlockBytes();
        return CryptographicUtils.generateUUIDFromBytes(blockBytes);
    }

    private byte[] computeBlockBytes() throws IOException {
        int bufferSize = Integer.BYTES
                + prevRefs.size() * 2 * Long.BYTES
                + contentList.getSerializedSize()
                + proof.getSerializedSize();
        ByteBuf in = getLedgerBlockByteBuf(bufferSize, inherentWeight, prevRefs, contentList, proof);
        return in.array();
    }

    static <P extends SybilResistantElectionProof> ByteBuf getLedgerBlockByteBuf(
            int bufferSize, int inherentWeight, List<UUID> prevRefs, ContentList ContentList, P proof)
            throws IOException {
        ByteBuf in = Unpooled.buffer(bufferSize);
        in.writeInt(inherentWeight);
        for (UUID prev : prevRefs) {
            in.writeLong(prev.getMostSignificantBits());
            in.writeLong(prev.getLeastSignificantBits());
        }
        ContentList.getSerializer().serialize(ContentList, in);
        proof.getSerializer().serialize(proof, in);
        return in;
    }

    @Override
    public UUID getBlockId() {
        return blockId;
    }

    @Override
    public int getInherentWeight() {
        return inherentWeight;
    }

    @Override
    public List<UUID> getPrevRefs() {
        return prevRefs;
    }

    @Override
    public ContentList getContentList() {
        return contentList;
    }

    @Override
    public SybilResistantElectionProof getSybilElectionProof() {
        return proof;
    }

    static List<ValidatorSignature> deserializeValidatorSignatures(ByteBuf in) throws IOException {
        int numValidatorSignatures = in.readShort();
        List<ValidatorSignature> validatorSignatures = new ArrayList<>(numValidatorSignatures);
        ISerializer<ValidatorSignature> serializer = ValidatorSignature.serializer;
        for (int i = 0; i < numValidatorSignatures; i++)
            validatorSignatures.add(serializer.deserialize(in));
        return List.copyOf(validatorSignatures);
    }

    private List<ValidatorSignature> genValidatorSignaturesFromProposer(UUID blockId, KeyPair proposer) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        List<ValidatorSignature> validatorSignatures = new ArrayList<>(1);
        validatorSignatures.add(new ValidatorSignature(proposer, blockId));
        return validatorSignatures;
    }

    @Override
    public PublicKey getProposer() {
        assert (!validatorSignatures.isEmpty());
        return validatorSignatures.get(0).getValidatorKey();
    }

    @Override
    public boolean hasValidSemantics() {
        return false;
    }

    @Override
    public int getSerializedSize() throws IOException {
        return Integer.BYTES
                + Short.BYTES
                + prevRefs.size() * 2 * Long.BYTES
                + contentList.getSerializedSize()
                + proof.getSerializedSize()
                + computeValidatorSignaturesSize();
    }

    @Override
    public List<ValidatorSignature> getSignatures() {
        return validatorSignatures;
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public UUID getBlockingID() {
        return blockId;
    }

    static BroadcastValue deserializeInnerPojo(ByteBuf in) throws IOException {
        short innerClass = in.readShort();
        ISerializer<BroadcastValue> serializer = pojoSerializers.get(innerClass);
        return serializer.deserialize(in);
    }

    @Override
    public void addValidatorSignature(ValidatorSignature validatorSignature) {
        validatorSignatures.add(validatorSignature);
    }

    @Override
    public ISerializer<BroadcastValue> getSerializer() {
        return serializer;
    }

    private int computeValidatorSignaturesSize() throws IOException {
        int accum = 0;
        for (ValidatorSignature vs : validatorSignatures)
            accum += vs.getSerializedSize();
        return accum;
    }
}
