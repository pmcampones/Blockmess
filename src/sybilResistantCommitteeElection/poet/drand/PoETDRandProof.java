package sybilResistantCommitteeElection.poet.drand;

import io.netty.buffer.ByteBuf;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.io.IOException;
import java.util.Arrays;

public class PoETDRandProof extends ProtoPojoAbstract implements SybilElectionProof {

    public static final short ID = 1107;

    private final int drandRound;

    private final byte[] randomness;

    private final int salt;

    private final int waitTime;

    public PoETDRandProof(int drandRound, byte[] randomness,
                          int salt, int waitTime) {
        super(ID);
        this.drandRound = drandRound;
        this.randomness = randomness;
        this.salt = salt;
        this.waitTime = waitTime;
    }

    public int getWaitTime() {
        return waitTime;
    }

    @Override
    public int getSerializedSize() throws IOException {
        return  Integer.BYTES         //round
                + randomness.length
                + Integer.BYTES         //salt
                + Integer.BYTES;        //time
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo pojo, ByteBuf out) throws IOException {
            if (!(pojo instanceof PoETDRandProof))
                throw new IOException(String.format(
                        "Expected to serialize a %s, but instead got a %s",
                        PoETDRandProof.class.getSimpleName(),
                        pojo.getClass().getSimpleName()
                ));
            PoETDRandProof dRandProof = (PoETDRandProof) pojo;
            out.writeInt(dRandProof.drandRound);
            out.writeInt(dRandProof.randomness.length);
            out.writeBytes(dRandProof.randomness);
            out.writeInt(dRandProof.salt);
            out.writeInt(dRandProof.waitTime);
        }

        @Override
        public ProtoPojoAbstract deserialize(ByteBuf in) {
            int drandRound = in.readInt();
            byte[] randomness = new byte[in.readInt()];
            in.readBytes(randomness);
            int salt = in.readInt();
            int waitTime = in.readInt();
            return new PoETDRandProof(drandRound, randomness, salt, waitTime);
        }
    };

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PoETDRandProof))
            return false;
        PoETDRandProof otherProof = (PoETDRandProof) other;
        return drandRound == otherProof.drandRound
                && Arrays.equals(randomness, otherProof.randomness)
                && salt == otherProof.salt
                && waitTime == otherProof.waitTime;
    }
}
