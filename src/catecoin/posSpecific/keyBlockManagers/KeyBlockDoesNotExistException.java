package catecoin.posSpecific.keyBlockManagers;

import java.util.UUID;

public class KeyBlockDoesNotExistException extends Exception {

    private static final String DEFAULT_MESSAGE =
            "Requested key block %s does not exist, or has not been correctly recorded.";

    public KeyBlockDoesNotExistException(UUID block) {
        super(String.format(DEFAULT_MESSAGE, block));
    }
}
