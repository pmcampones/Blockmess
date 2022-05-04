package ledger.prototype;

public class PrototypeAlreadyDefinedException extends Exception {

    private static final String DEFAULT_MESSAGE =
            "Cannot define a prototype because another has already been defined.";

    public PrototypeAlreadyDefinedException() {
        super(DEFAULT_MESSAGE);
    }

}
