package peerSamplingProtocols.fullMembership.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SampleMessage extends ProtoMessage {

    public final static short ID = 1400;

    public final Set<Host> sample;

    public SampleMessage(Set<Host> sample) {
        super(ID);
        this.sample = sample;
    }

    @Override
    public String toString() {
        return "ShuffleMessage{" +
                "subset=" + sample +
                '}';
    }

    public static ISerializer<SampleMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(SampleMessage sampleMessage, ByteBuf out) throws IOException {
            out.writeInt(sampleMessage.sample.size());
            for (Host h : sampleMessage.sample)
                Host.serializer.serialize(h, out);
        }

        @Override
        public SampleMessage deserialize(ByteBuf in) throws IOException {
            int size = in.readInt();
            Set<Host> subset = new HashSet<>(size, 1);
            for (int i = 0; i < size; i++)
                subset.add(Host.serializer.deserialize(in));
            return new SampleMessage(subset);
        }
    };
}
