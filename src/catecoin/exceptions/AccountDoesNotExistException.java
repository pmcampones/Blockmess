package catecoin.exceptions;

import java.util.UUID;

public class AccountDoesNotExistException extends Exception {

    public static final String DEFAULT_MESSAGE =
            "The indicated account %s does not exist.";

    public AccountDoesNotExistException(UUID account) {
        super(String.format(DEFAULT_MESSAGE, account.toString()));
    }
}
