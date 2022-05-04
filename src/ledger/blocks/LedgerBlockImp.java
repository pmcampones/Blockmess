package ledger.blocks;

import catecoin.blocks.AdversarialValidatorSignature;
import catecoin.blocks.ValidatorSignature;
import catecoin.blocks.ValidatorSignatureImp;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import main.CryptographicUtils;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantElection.SybilElectionProof;

import java.io.IOException;
import java.security.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LedgerBlockImp<C extends BlockContent<? extends ProtoPojo>, P extends SybilElectionProof> extends ProtoPojoAbstract
        implements LedgerBlock<C, P> {

    public static final short ID = 9888;

    private final UUID blockId;

    private final int inherentWeight;

    private final List<UUID> prevRefs;

    private final C blockContent;

    private final P proof;

    private final List<ValidatorSignature> validatorSignatures;

    /**
     * This constructor is meant to be used by the block proposer to either disseminate the block
     * if this DL uses a single validator per block; or to be send the block to other validators
     * if the DL is using a BFT-committee to validate blocks.
     */
    public LedgerBlockImp(int inherentWeight, List<UUID> prevRefs, C blockContent,
                          P proof, KeyPair proposer) throws IOException,
            NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        super(ID);
        this.inherentWeight = inherentWeight;
        this.prevRefs = prevRefs;
        this.blockContent = blockContent;
        this.proof = proof;
        this.blockId = computeBlockId();
        this.validatorSignatures = genValidatorSignaturesFromProposer(blockId, proposer);
    }

    /**
     * This constructor is meant to be used by nodes receiving the Block and called during the deserialization.
     */
    private LedgerBlockImp(int inherentWeight, List<UUID> prevRefs, C blockContent,
                           P proof, List<ValidatorSignature> validatorSignatures) throws IOException {
        super(ID);
        this.inherentWeight = inherentWeight;
        this.prevRefs = prevRefs;
        this.blockContent = blockContent;
        this.proof = proof;
        this.blockId = computeBlockId();
        this.validatorSignatures = validatorSignatures;
    }

    public LedgerBlockImp(UUID blockId, int inherentWeight, List<UUID> prevRefs, C blockContent,
                           P proof, List<ValidatorSignature> validatorSignatures) {
        super(ID);
        this.blockId = blockId;
        this.inherentWeight = inherentWeight;
        this.prevRefs = prevRefs;
        this.blockContent = blockContent;
        this.proof = proof;
        this.validatorSignatures = validatorSignatures;
    }

    /**
     * This constructor is used when we do not want to compute the blockId and instead wish to receive its value.
     * <p>An example of this is when a block class extends this and the computation of the blockId changes because
     * of some extra parameters.</p>
     */
    LedgerBlockImp(UUID blockId, int inherentWeight, List<UUID> prevRefs, C blockContent,
                   P proof, KeyPair proposer, short classId)
            throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        super(classId);
        this.blockId = blockId;
        this.inherentWeight = inherentWeight;
        this.prevRefs = prevRefs;
        this.blockContent = blockContent;
        this.proof = proof;
        this.validatorSignatures = genValidatorSignaturesFromProposer(blockId, proposer);
    }

    LedgerBlockImp(UUID blockId, int inherentWeight, List<UUID> prevRefs, C blockContent,
                   P proof, List<ValidatorSignature> validatorSignatures, short classId) {
        super(classId);
        this.blockId = blockId;
        this.inherentWeight = inherentWeight;
        this.prevRefs = prevRefs;
        this.blockContent = blockContent;
        this.proof = proof;
        this.validatorSignatures = validatorSignatures;
    }

    private List<ValidatorSignature> genValidatorSignaturesFromProposer(UUID blockId, KeyPair proposer) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        List<ValidatorSignature> validatorSignatures = new ArrayList<>(1);
        validatorSignatures.add(new ValidatorSignatureImp(proposer, blockId));
        return validatorSignatures;
    }

    private UUID computeBlockId() throws IOException {
        byte[] blockBytes = computeBlockBytes();
        return CryptographicUtils.generateUUIDFromBytes(blockBytes);
    }

    private byte[] computeBlockBytes() throws IOException {
        int bufferSize = Integer.BYTES
                + prevRefs.size() * 2 * Long.BYTES
                + blockContent.getSerializedSize()
                + proof.getSerializedSize();
        ByteBuf in = getLedgerBlockByteBuf(bufferSize, inherentWeight, prevRefs, blockContent, proof);
        return in.array();
    }

    static <C extends BlockContent, P extends SybilElectionProof> ByteBuf getLedgerBlockByteBuf(
            int bufferSize, int inherentWeight, List<UUID> prevRefs, C blockContent, P proof)
            throws IOException {
        ByteBuf in = Unpooled.buffer(bufferSize);
        in.writeInt(inherentWeight);
        for (UUID prev : prevRefs) {
            in.writeLong(prev.getMostSignificantBits());
            in.writeLong(prev.getLeastSignificantBits());
        }
        blockContent.getSerializer().serialize(blockContent, in);
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
    public C getBlockContent() {
        return blockContent;
    }

    @Override
    public P getSybilElectionProof() {
        return proof;
    }

    @Override
    public List<ValidatorSignature> getSignatures() {
        return validatorSignatures;
    }

    @Override
    public void addValidatorSignature(ValidatorSignature validatorSignature) {
        validatorSignatures.add(validatorSignature);
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
                + blockContent.getSerializedSize()
                + proof.getSerializedSize()
                + computeValidatorSignaturesSize();
    }

    private int computeValidatorSignaturesSize() throws IOException {
        int accum = 0;
        for (ValidatorSignature vs : validatorSignatures)
            accum += vs.getSerializedSize();
        return accum;
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public UUID getBlockingID() {
        return blockId;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo protoPojo, ByteBuf out) throws IOException {
            LedgerBlock<BlockContent<ProtoPojo>, SybilElectionProof> block = (LedgerBlock) protoPojo;
            out.writeInt(block.getInherentWeight());
            serializePrevs(block.getPrevRefs(), out);
            serializePojo(block.getBlockContent(), out);
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

        private void serializePojo(ProtoPojo pojo, ByteBuf out) throws IOException {
            out.writeShort(pojo.getClassId());
            ISerializer<ProtoPojo> serializer = pojo.getSerializer();
            serializer.serialize(pojo, out);
        }

        private void serializeValidatorSignatures(List<ValidatorSignature> validatorSignatures, ByteBuf out)
                throws IOException {
            out.writeShort(validatorSignatures.size());
            ISerializer<AdversarialValidatorSignature> serializer = ValidatorSignatureImp.serializer;
            for (ValidatorSignature validatorSignature : validatorSignatures)
                serializer.serialize((AdversarialValidatorSignature) validatorSignature, out);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) throws IOException {
            int inherentWeight = in.readInt();
            List<UUID> prevRefs = ProtoPojo.deserializeUuids(in);
            BlockContent blockContent = (BlockContent) deserializeInnerPojo(in);
            SybilElectionProof proof = (SybilElectionProof) deserializeInnerPojo(in);
            List<ValidatorSignature> validatorSignatures = deserializeValidatorSignatures(in);
            return new LedgerBlockImp<>(inherentWeight, prevRefs,
                    blockContent, proof, validatorSignatures);
        }

    };

    static ProtoPojo deserializeInnerPojo(ByteBuf in) throws IOException {
        short innerClass = in.readShort();
        ISerializer<ProtoPojo> serializer = pojoSerializers.get(innerClass);
        return serializer.deserialize(in);
    }

    static List<ValidatorSignature> deserializeValidatorSignatures(ByteBuf in) throws IOException {
        int numValidatorSignatures = in.readShort();
        List<ValidatorSignature> validatorSignatures = new ArrayList<>(numValidatorSignatures);
        ISerializer<AdversarialValidatorSignature> serializer = ValidatorSignatureImp.serializer;
        for (int i = 0; i < numValidatorSignatures; i++)
            validatorSignatures.add(serializer.deserialize(in));
        return List.copyOf(validatorSignatures);
    }
}
