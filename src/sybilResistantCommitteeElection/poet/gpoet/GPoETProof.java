package sybilResistantCommitteeElection.poet.gpoet;

import io.netty.buffer.ByteBuf;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.io.IOException;

public class GPoETProof extends ProtoPojoAbstract implements SybilElectionProof {

    public static final short ID = 9182;

    public static final int SERIALIZED_SIZE = Integer.BYTES;

    private final int nonce;

    public GPoETProof(int nonce) {
        super(ID);
        this.nonce = nonce;
    }

    public int getNonce() {
        return nonce;
    }

    @Override
    public int getSerializedSize() throws IOException {
        return Integer.BYTES;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo protoPojo, ByteBuf out) {
            GPoETProof proof = (GPoETProof) protoPojo;
            out.writeInt(proof.nonce);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) {
            int nonce = in.readInt();
            return new GPoETProof(nonce);
        }
    };

}
