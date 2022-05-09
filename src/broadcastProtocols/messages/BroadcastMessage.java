package broadcastProtocols.messages;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import valueDispatcher.DispatcherWrapper;

import java.util.UUID;

public interface BroadcastMessage {

    UUID getMid();

    DispatcherWrapper getVal();

    boolean isBlocking();

    UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast;
}
