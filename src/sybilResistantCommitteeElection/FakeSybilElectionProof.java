package sybilResistantCommitteeElection;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import io.netty.buffer.ByteBuf;
import main.ProtoPojo;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.IDGenerator;

import java.io.IOException;
import java.util.UUID;

public class FakeSybilElectionProof implements SybilElectionProof {

    public static final short ID = IDGenerator.genId();

    @Override
    public int getSerializedSize() throws IOException {
        return 0;
    }

    @Override
    public short getClassId() {
        return ID;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {
        @Override
        public void serialize(ProtoPojo protoPojo, ByteBuf byteBuf) {}

        @Override
        public ProtoPojo deserialize(ByteBuf byteBuf) {
            return new FakeSybilElectionProof();
        }
    };

    @Override
    public boolean isBlocking() {
        return false;
    }

    @Override
    public UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast {
        return null;
    }
}
