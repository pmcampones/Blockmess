package broadcastProtocols.messages;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import main.ProtoPojo;

import java.util.UUID;

public interface BroadcastMessage {

    UUID getMid();

    ProtoPojo getVal();

    boolean isBlocking();

    UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast;
}
