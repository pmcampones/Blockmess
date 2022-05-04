package catecoin.utxos;

import utils.CryptographicUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.PublicKey;
import java.util.Random;
import java.util.UUID;

/**
 * Transaction output used in a block being broadcast and validated.
 * It does not contain all fields pertaining to itself. A UTXO should contain information on its origin and destination.
 * The missing information is repeated in the transaction itself. Found in the SimplifiedTransaction class.
 * The necessary validation information is retrieved from the SimplifiedTransaction.
 * The contents in this class are posteriorly converted into {@link StorageUTXO}s when they are decoupled from the transactions.
 * Replaces the FatUTXO class, who add all the necessary (and excessive) contents for local validation,
 *  but proved very heavy on the network load as blocks were being broadcast.
 */
public class SlimUTXO implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int SERIALIZED_SIZE = 2 * Integer.BYTES; //nonce.BYTES + amount.BYTES

    /**
     * Uniquely identifies the TxOutput and is used to sign the transaction where it originated.
     * It is computed from the UTXO's fields, so any attempt to forge the UTXO's contents are detected.
     * The identifier is a function of not only the fields in this class, but also the identifiers of its origin and destination.
     *  These two keys, while not being fields of this class, are fields of the conceptual UTXO represented by this instance.
     * This object is transient because it is computed in its creation and by nodes receiving the UTXO.
     * Although the tag is merely illustrative, because the TxOutput won't (and mustn't) be serialized directly.
     */
    private transient final UUID outputId;

    private final int nonce, amount;

    /**
     * Constructor called by the issuer of the transaction creating this UTXO.
     * @param amount The number of coins being transferred from the originator to the destination
     * @param origin The originator of the transaction
     * @param destination The destination of the UTXO, who may be different from the destination of the transaction.
     * @throws IOException If the Keys received are malformed this is thrown.
     */
    public SlimUTXO(int amount, PublicKey origin, PublicKey destination)
            throws IOException {
        this.nonce = new Random().nextInt();
        this.amount = amount;
        this.outputId = computeOutputUUID(origin, destination);
    }

    /**
     * Constructor called by the nodes receiving the transaction through the broadcast protocol.
     * The outputId needs to be reconstructed on arrival because otherwise the Adversary could modify the amount and use a valid ID corresponding to a lower amount.
     * @param fields Fields of this UTXO independent of their placement in a transaction. Comprised of a nonce and the UTXO amount.
     * @param origin The originator of the transaction
     * @param destination The destination of the UTXO, who may be different from the destination of the transaction.
     */
    public SlimUTXO(SlimUTXOIndependentFields fields, PublicKey origin, PublicKey destination)
            throws IOException {
        this.nonce = fields.getNonce();
        this.amount = fields.getAmount();
        this.outputId = computeOutputUUID(origin, destination);
    }

    private UUID computeOutputUUID(PublicKey origin, PublicKey destination) throws IOException {
        byte[] byteFields = getOutputFields(origin, destination);
        return CryptographicUtils.generateUUIDFromBytes(byteFields);
    }

    private byte[] getOutputFields(PublicKey origin, PublicKey destination) throws IOException {
        try (var out = new ByteArrayOutputStream();
             var oout = new ObjectOutputStream(out)) {
            oout.writeObject(origin);
            oout.writeObject(destination);
            oout.writeInt(nonce);
            oout.writeInt(amount);
            oout.flush();
            oout.flush();
            return out.toByteArray();
        }
    }

    public UUID getId() {
        return outputId;
    }

    public int getNonce() {
        return nonce;
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SlimUTXO && outputId.equals(((SlimUTXO) other).outputId);
    }

    @Override
    public String toString() {
        return outputId.toString();
    }

}
