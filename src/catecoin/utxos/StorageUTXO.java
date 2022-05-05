package catecoin.utxos;

import catecoin.txs.Transaction;
import utils.CryptographicUtils;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

/**
 * Contents of a UTXO after it is validated and decoupled from its transaction.
 * It only contains the fields required for the validation of future transactions.
 * Is formed from the contents of a {@link UTXO} and a {@link Transaction}.
 */
public class StorageUTXO {

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
    private final PublicKey owner;

    /**
     * The amount of coins in this transaction output.
     * Used to ensure the amount of input coins in a {@link Transaction} equals the number of coins in the output.
     */
    private final int amount;


    public StorageUTXO(UUID id, int amount, PublicKey owner) {
        this.id = id;
        this.amount = amount;
        this.owner = owner;
    }

    public UUID getId() {
        return id;
    }

    public PublicKey getUTXOOwner() {
        return owner;
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StorageUTXO
                && ((StorageUTXO) other).id.equals(id);
    }

    public byte[] getSerializedFormat() {
        try (var out = new ByteArrayOutputStream();
            var dout = new DataOutputStream(out)) {
            dout.writeLong(id.getMostSignificantBits());
            dout.writeLong(id.getLeastSignificantBits());
            byte[] encoded = owner.getEncoded();
            dout.writeShort(encoded.length);
            dout.write(encoded);
            dout.writeInt(amount);
            dout.flush();
            out.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static StorageUTXO fromSerializedFormat(byte[] bytes) {
        try (var in = new ByteArrayInputStream(bytes);
             var din = new DataInputStream(in)) {
            UUID id = new UUID(din.readLong(), din.readLong());
            byte[] encoded = new byte[din.readShort()];
            din.readFully(encoded);
            PublicKey owner = CryptographicUtils.fromEncodedFormat(encoded);
            int amount = din.readInt();
            return new StorageUTXO(id, amount, owner);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

}
