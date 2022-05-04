package sybilResistantCommitteeElection.pos.sortition.proofs;

import io.netty.buffer.ByteBuf;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.UUID;

public class KeyBlockSortitionProof extends ProtoPojoAbstract implements LargeSortitionProof, KeyBlockReferencing {

    public static final short ID = 654;

    private final IncompleteSortitionProof proof;

    private final byte[] nextRoundRandomness;

    public KeyBlockSortitionProof(IncompleteSortitionProof proof, byte[] nextRoundRandomness) {
        super(ID);
        this.proof = proof;
        this.nextRoundRandomness = nextRoundRandomness;
    }

    @Override
    public int getRound() {
        return proof.getRound();
    }

    @Override
    public int getVotes() {
        return proof.getVotes();
    }

    @Override
    public UUID getKeyBlockId() {
        return proof.getKeyBlockId();
    }

    @Override
    public byte[] getHashProof() {
        return proof.getHashProof();
    }

    public byte[] getNextRoundRandomness() {
        return nextRoundRandomness;
    }

    @Override
    public int getSerializedSize() throws IOException {
        return proof.getSerializedSize() + nextRoundRandomness.length;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo pojo, ByteBuf out) throws IOException {
                if (!(pojo instanceof KeyBlockSortitionProof))
                    throw new IOException(String.format(
                            "Expected to serialize a %s, but instead got a %s.",
                            KeyBlockSortitionProof.class.getSimpleName(),
                            pojo.getClass().getSimpleName()
                    ));
                KeyBlockSortitionProof timelyProof = (KeyBlockSortitionProof) pojo;
                serializeInnerProof(timelyProof.proof, out);
                serializeRandomness(timelyProof.nextRoundRandomness, out);
        }

        private void serializeInnerProof(IncompleteSortitionProof proof, ByteBuf out) throws IOException {
            IncompleteSortitionProof.serializer.serialize(proof, out);
        }

        private void serializeRandomness(byte[] randomness, ByteBuf out) {
            out.writeShort(randomness.length);
            out.writeBytes(randomness);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) throws IOException {
            IncompleteSortitionProof proof = deserializeProof(in);
            byte[] nextRoundRandomness = deserializeRandomness(in);
            return new KeyBlockSortitionProof(proof, nextRoundRandomness);
        }

        private IncompleteSortitionProof deserializeProof(ByteBuf in) throws IOException {
            ISerializer<ProtoPojo> serializer = IncompleteSortitionProof.serializer;
            ProtoPojo innerPojo = serializer.deserialize(in);
            if (!(innerPojo instanceof IncompleteSortitionProof))
                throw new IOException(String.format(
                        "Expected to serialize a %s but instead got a %s",
                        IncompleteSortitionProof.class.getSimpleName(),
                        innerPojo.getClass().getSimpleName()
                ));
            return (IncompleteSortitionProof) innerPojo;
        }

        private byte[] deserializeRandomness(ByteBuf in) {
            byte[] randomness = new byte[in.readShort()];
            in.readBytes(randomness);
            return randomness;
        }
    };
}
