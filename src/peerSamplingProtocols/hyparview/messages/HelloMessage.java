package peerSamplingProtocols.hyparview.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class HelloMessage extends ProtoMessage {
    public final static short MSG_CODE = 3405;

    //prio == true -> high; prio == false --> low
    private boolean priority;

    public HelloMessage(boolean priority) {
        super(HelloMessage.MSG_CODE);
        this.priority = priority;
    }

    @Override
    public String toString() {
        return "HelloMessage{" +
                "priority=" + priority +
                '}';
    }

    public boolean isPriority() {
        return priority;
    }

    public static final ISerializer<HelloMessage> serializer = new ISerializer<>() {

        @Override
        public void serialize(HelloMessage m, ByteBuf out) {
            out.writeBoolean(m.priority);
        }

        @Override
        public HelloMessage deserialize(ByteBuf in) {
            boolean priority = in.readBoolean();
            return new HelloMessage(priority);
        }
    };
}
