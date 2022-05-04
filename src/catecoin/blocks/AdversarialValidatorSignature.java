package catecoin.blocks;

public interface AdversarialValidatorSignature extends ValidatorSignature {

    /**
     * Used for testing and for simulating the adversary.
     * Modifies the signature of a Validator.
     */
    void forgeSignature(byte[] forgedSignature);

}
