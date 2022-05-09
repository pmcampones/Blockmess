package broadcastProtocols;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;

import java.util.UUID;

public abstract class BroadcastValueAbstract implements BroadcastValue {

    private final short id;

    protected BroadcastValueAbstract(short id) {
        this.id = id;
    }

    @Override
    public final short getClassId() {
        return id;
    }

    @Override
    public boolean isBlocking() {
        return false;
    }

    @Override
    public UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast {
        throw new InnerValueIsNotBlockingBroadcast();
    }

}
