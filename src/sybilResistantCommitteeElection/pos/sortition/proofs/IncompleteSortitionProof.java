package sybilResistantCommitteeElection.pos.sortition.proofs;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import io.netty.buffer.ByteBuf;
import main.CryptographicUtils;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.UUID;

import static catecoin.validators.SortitionProofValidator.computeHashVal;

public class IncompleteSortitionProof extends ProtoPojoAbstract implements LargeSortitionProof {

    public static final short ID = 459;

    private final UUID proofId;

    private final int round;

    private final int votes;

    private final UUID keyBlockId;

    private final byte[] hashProof;

    public IncompleteSortitionProof(int round, int votes, UUID keyBlockId, byte[] hashProof) {
        super(ID);
        this.round = round;
        this.votes = votes;
        this.keyBlockId = keyBlockId;
        this.hashProof = hashProof;
        this.proofId = computeProofId();
    }

    private UUID computeProofId() {
        //Makes unnecessary hash
        return CryptographicUtils.generateUUIDFromBytes(hashProof);
    }

    @Override
    public int getRound() {
        return round;
    }

    @Override
    public int getVotes() {
        return votes;
    }

    @Override
    public UUID getKeyBlockId() {
        return keyBlockId;
    }

    @Override
    public byte[] getHashProof() {
        return hashProof;
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast {
        return proofId;
    }

    @Override
    public int getSerializedSize() throws IOException {
        return 2 * Integer.BYTES + 2 * Long.BYTES + hashProof.length;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {
        @Override
        public void serialize(ProtoPojo pojo, ByteBuf out) throws IOException {
            if (!(pojo instanceof IncompleteSortitionProof))
                throw new IOException(String.format(
                        "Expected to serialize a %s, but received something else",
                        IncompleteSortitionProof.class.getSimpleName()
                ));
            IncompleteSortitionProof proof = (IncompleteSortitionProof) pojo;
            out.writeInt(proof.round);
            out.writeInt(proof.votes);
            out.writeLong(proof.keyBlockId.getMostSignificantBits());
            out.writeLong(proof.keyBlockId.getLeastSignificantBits());
            out.writeInt(proof.hashProof.length);
            out.writeBytes(proof.hashProof);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) {
            int round = in.readInt();
            int votes = in.readInt();
            UUID keyBlockId = new UUID(in.readLong(), in.readLong());
            byte[] proofSignature = new byte[in.readInt()];
            in.readBytes(proofSignature);
            return new IncompleteSortitionProof(round, votes, keyBlockId, proofSignature);
        }
    };

    public boolean hasPriorityOver(IncompleteSortitionProof other) {
        return compareTo(other) > 0;
    }

    public int compareTo(IncompleteSortitionProof other) {
        int diffRounds = this.round - other.round;
        if (diffRounds != 0)
            return diffRounds;

        /*
        TODO: Compare weights of the referenced key blocks
        Will do this once I make the key block manager a singleton.
         */

        int diffVotes = this.votes - other.votes;
        if (diffVotes != 0)
            return diffVotes;

        long diffHashVals = computeHashVal(this.hashProof) - computeHashVal(other.hashProof);
        diffHashVals = Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, diffHashVals));
        return (int) diffHashVals;
    }

}