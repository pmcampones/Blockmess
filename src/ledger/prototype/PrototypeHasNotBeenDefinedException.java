package ledger.prototype;

public class PrototypeHasNotBeenDefinedException extends Exception {

    private static final String DEFAULT_MESSAGE =
            "Cannot provide a Ledger because no prototype has been defined.";

    public PrototypeHasNotBeenDefinedException() {
        super(DEFAULT_MESSAGE);
    }

}
