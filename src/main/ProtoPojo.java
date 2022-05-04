package main;

import broadcastProtocols.BlockingBroadcast;
import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Represents the objects being transferred between nodes in the application.
 * <p>Contains a set of utilities used by the objects implementing this interface.</p>
 * <p>Notably a means to select a serializer based on the object's unique identifier,</p>
 *  and indicates the common cryptographic algorithms used to sign/verify and hash these objects.
 */
public interface ProtoPojo extends Serializable {

    /**
     * Maps each class implementing this interface to its serializer.
     * Doing so allows a node receiving information to select the correct serializer without concern for context
     */
    Map<Short, ISerializer<ProtoPojo>> pojoSerializers = new ConcurrentHashMap<>();

    /**
     * @return Implementing class specific identifier.
     */
    short getClassId();

    /**
     * @return Serializer that serializes/deserializes the objects as these are transferred between nodes.
     */
    ISerializer<ProtoPojo> getSerializer();

    /**
     * Indicates whether the contents in a message should block its broadcast until being validated.
     * <p>The broadcast protocol in itself is not blocked, as other values can be disseminated.</p>
     */
    boolean isBlocking();

    /**
     * Returns the identifier of the pojo that blocks the broadcast protocol.
     * <p>A pojo blocks the broadcast protocol if it implements {@link BlockingBroadcast}.</p>
     * <p>The value returned might not be from this pojo in specific, but rather from an inner value in it.</p>
     * @return The blocking broadcast pojo identifier.
     * @throws InnerValueIsNotBlockingBroadcast This pojo, nor any of its inner values blocks the broadcast protocol.
     */
    UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast;

    /**
     * Identifies if all elements in a collection are unique.
     * <p>In Transactions all UTXOs must be unique, and in blocks all Transactions must be unique as well.</p>
     * <p>The distinctiveness of the elements could be assured by keeping them in a sorted set.</p>
     * <p>However by doing so the Adversary could create repeats without being detected.</p>
     * <p>So, it could place a heavy load on the network with very large blocks with repeated content.</p>
     * <p>These would be considered valid, and the state of the system would remain correct.</p>
     * <p>An alternative to this approach would be to identify repeats on the deserialization.
     * It would also be faster.</p>
     * <p>However, it's not the deserializer's job to identify repeats, and it would require us to launch the wrong exception.
     * I'll probably change my mind later tho.</p>
     *
     * TODO Currently we are also verifying if the input received equals the reserved input from the Validator.
     *      This happens because all nodes can use this input.
     *      This should not be allowed in a real application.
     *          Not only because of the practical consequences of having a always valid and forgeable input;
     *          but also because it is contrary to our efforts of decoupling blocks from the Application.
     *
     * @param <T> UTXOs or Transactions, however it could be anything.
     * @param col Elements that we will check for repeats.
     * @return true if all elements in the argument collection are distinct (except the reserved inputs, which are always valid).
     */
    static <T> boolean allUnique(Stream<T> col) {
        Set<T> detector = new HashSet<>();
        return col.sequential().allMatch(detector::add);
    }

    static void serializeUuids(List<UUID> ids, ByteBuf out) {
        out.writeShort(ids.size());
        for (UUID id : ids) {
            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());
        }
    }

    static List<UUID> deserializeUuids(ByteBuf in) {
        int numIds = in.readShort();
        List<UUID> ids = new ArrayList<>(numIds);
        for (int i = 0; i < numIds; i++) {
            ids.add(new UUID(in.readLong(), in.readLong()));
        }
        return ids;
    }
}
