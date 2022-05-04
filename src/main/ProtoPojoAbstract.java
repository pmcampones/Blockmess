package main;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

public abstract class ProtoPojoAbstract implements ProtoPojo {

    private final short id;

    protected ProtoPojoAbstract(short id) {
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
