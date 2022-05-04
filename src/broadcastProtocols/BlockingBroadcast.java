package broadcastProtocols;

/**
 * Indicated that this object blocks its own broadcast until it is validated.
 * <p>The broadcast protocol itself is not blocked, as other messages can be broadcast.</p>
 */
public interface BlockingBroadcast {}
