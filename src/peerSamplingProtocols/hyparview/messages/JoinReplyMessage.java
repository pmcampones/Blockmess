package peerSamplingProtocols.hyparview.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class JoinReplyMessage extends ProtoMessage {
    public final static short MSG_CODE = 3402;


    public JoinReplyMessage() {
        super(MSG_CODE);
    }

    @Override
    public String toString() {
        return "JoinReplyMessage{}";
    }

    public static final ISerializer<JoinReplyMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(JoinReplyMessage m, ByteBuf out) {

        }

        @Override
        public JoinReplyMessage deserialize(ByteBuf in) {
            return new JoinReplyMessage();
        }
    };
}
