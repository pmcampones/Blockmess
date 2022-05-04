package catecoin.exceptions;

import java.util.UUID;

public class InputNotFoundException extends Exception {

    private static final String DEFAULT_MESSAGE =
            "Could not find input %s";

    public InputNotFoundException(UUID input) {
        super(String.format(DEFAULT_MESSAGE, input));
    }
}
