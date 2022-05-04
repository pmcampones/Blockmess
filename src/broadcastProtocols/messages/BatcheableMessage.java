package broadcastProtocols.messages;

import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Messages extending this class implement the functionalities allowing them
 * to be transferred in batches for recovery purposes.
 * <p>Concretely, these functionalities consist in allowing the instances
 * to return their serializers on a per instance basis.</p>
 * <p>Although the specific use of these messages currently is a recovery mechanism,
 * in the future, batches of messages can be used for performance reasons.
 * To reduce the amount of messages exchanged,
 * and mitigate the effect of the RTTs in small messages.</p>
 * <p>Should the latter approach be considered,
 * it's preferable to generate the batches at the application level.</p>
 * <p>By batching messages of the same kind at the Broadcast level,
 * information in the headers will be unnecessarily repeated.</p>
 * <p>The parsing and optimization of these headers (see {@link valueDispatcher.ValueDispatcher})
 * is not the responsibility of the broadcast protocol,
 * and it should not (unless efficiency is paramount) be handled by these protocols.</p>
 */
public abstract class BatcheableMessage extends ProtoMessage {

    public static final Map<Short, ISerializer<BatcheableMessage>> serializers = new HashMap<>();

    public BatcheableMessage(short id) {
        super(id);
    }

    public abstract ISerializer<BatcheableMessage> getSerializer();

    public abstract UUID getMid();

}
