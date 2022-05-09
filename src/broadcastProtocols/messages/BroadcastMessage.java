package broadcastProtocols.messages;

import broadcastProtocols.BroadcastValue;
import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;

import java.util.UUID;

public interface BroadcastMessage {

    UUID getMid();

    BroadcastValue getVal();

    boolean isBlocking();

    UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast;
}
