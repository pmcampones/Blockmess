package catecoin.utxos;

import catecoin.txs.Transaction;
import utils.CryptographicUtils;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

/**
 * Contents of a {@link StorageUTXO} with an encoded public key.
 * <p>This encoding allows the UTXO to be placed in a Json file.</p>
 */
public class JsonAcceptedUTXO {

    /**
     * Identifier of the UTXO.
     * Corresponds to the identifier of the matching {@link UTXO} instance that preceds this.
     * Used to ensure future {@link Transaction}s do not reference non existent UTXOs
     */
    private final UUID id;

    /**
     * Owner of the UTXO.
     * Corresponds to either the origin or the destination of the {@link Transaction} where the UTXO was issued.
     * Used to ensure the issuer of a transaction only uses inputs belonging to it.
     */
    private final byte[] ownerEncoded;

    /**
     * The amount of coins in this transaction output.
     * Used to ensure the amount of input coins in a {@link Transaction} equals the number of coins in the output.
     */
    private final int amount;


    public JsonAcceptedUTXO(StorageUTXO original) {
        this.id = original.getId();
        this.ownerEncoded = original.getUTXOOwner().getEncoded();
        this.amount = original.getAmount();
    }

    public StorageUTXO fromJsonAcceptedUTXO() throws NoSuchAlgorithmException, InvalidKeySpecException {
        return new StorageUTXO(id, amount, CryptographicUtils.fromEncodedFormat(ownerEncoded));
    }

}
