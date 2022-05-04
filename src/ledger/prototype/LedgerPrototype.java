package ledger.prototype;

import ledger.PrototypicalLedger;

import java.util.UUID;

/**
 * Defines a static Ledger to be used as a Prototype to create several others
 * according with the Prototype design pattern.
 */
public class LedgerPrototype {

    private static PrototypicalLedger prototype = null;

    public static void setPrototype(PrototypicalLedger ledger)
            throws PrototypeAlreadyDefinedException {
        if (prototype != null)
            throw new PrototypeAlreadyDefinedException();
        prototype = ledger;
    }

    public static PrototypicalLedger getLedgerCopy(UUID id)
            throws PrototypeHasNotBeenDefinedException {
        if (prototype == null)
            throw new PrototypeHasNotBeenDefinedException();
        return prototype.clonePrototype(id);
    }

}
