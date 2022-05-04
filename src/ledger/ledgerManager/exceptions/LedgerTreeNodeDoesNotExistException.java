package ledger.ledgerManager.exceptions;

import java.util.UUID;

public class LedgerTreeNodeDoesNotExistException extends Exception {

    private static final String DEFAULT_MESSAGE =
            "LedgerTree node with ID '%s' does not exist.";

    public LedgerTreeNodeDoesNotExistException(UUID node) {
        super(String.format(DEFAULT_MESSAGE, node.toString()));
    }

}
