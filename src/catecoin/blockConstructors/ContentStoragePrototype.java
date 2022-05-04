package catecoin.blockConstructors;

import ledger.prototype.PrototypeAlreadyDefinedException;
import ledger.prototype.PrototypeHasNotBeenDefinedException;

public class ContentStoragePrototype {

    private static PrototypicalContentStorage prototype = null;

    public static void setPrototype(PrototypicalContentStorage contentStorage)
            throws PrototypeAlreadyDefinedException {
        if (prototype != null)
            throw new PrototypeAlreadyDefinedException();
        prototype = contentStorage;
    }

    public static PrototypicalContentStorage getPrototype()
            throws PrototypeHasNotBeenDefinedException {
        if (prototype == null)
            throw new PrototypeHasNotBeenDefinedException();
        return prototype.clonePrototype();
    }

}
