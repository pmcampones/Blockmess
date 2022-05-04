package catecoin.exceptions;

public class NotEnoughCoinsException extends Exception {

    private static final String DEFAULT_MESSAGE = "Insufficient Balance. Current=%d, Requested=%d";

    public NotEnoughCoinsException(int current, int requested) {
        super(String.format(DEFAULT_MESSAGE, current, requested));
    }
}