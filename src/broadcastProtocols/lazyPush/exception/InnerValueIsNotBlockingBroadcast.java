package broadcastProtocols.lazyPush.exception;

public class InnerValueIsNotBlockingBroadcast extends Exception {

    public static final String DEFAULT_MESSAGE =
            "Value proposed is does not block the broadcast.";

    public InnerValueIsNotBlockingBroadcast() {
        super(DEFAULT_MESSAGE);
    }
}
