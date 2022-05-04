package catecoin.mempoolManager;

public class UnexpectedChunkTypeException extends Exception {

    public static final String DEFAULT_MESSAGE =
            "Expecting a chunk of type %s, but instead got something else.";

    public UnexpectedChunkTypeException(String expectedType) {
        super(String.format(DEFAULT_MESSAGE, expectedType));
    }

}
