package catecoin.txs;

import catecoin.utxos.SlimUTXO;
import catecoin.utxos.SlimUTXOIndependentFields;
import main.CryptographicUtils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

public class SerializableTransaction {

    /**
     * Encoded formats of the origin and destination public keys, as found in {@link SlimTransaction}
     */
    private final byte[] origin, destination;

    /**
     * Identifiers of the UTXO inputs used in the transaction.
     */
    private final List<UUID> inputs;

    /**
     * List of simplified UTXOs belonging to the destination of the transaction.
     */
    private final List<SlimUTXOIndependentFields> outputsDestination;

    /**
     * List of simplified UTXOs belonging to the originator of the transaction.
     * These are used for the origin to obtain the excess UTXO coins sent in the input.
     */
    private final List<SlimUTXOIndependentFields> outputsOrigin;

    private final byte[] originSignature;


    public SerializableTransaction(SlimTransaction tx) {
        this.origin = tx.getOrigin().getEncoded();
        this.destination = tx.getDestination().getEncoded();
        this.inputs = tx.getInputs();
        this.outputsDestination = getSlimUTXOIndependentFields(tx.getOutputsDestination());
        this.outputsOrigin = getSlimUTXOIndependentFields(tx.getOutputsOrigin());
        this.originSignature = tx.getOriginSignature();
    }

    private List<SlimUTXOIndependentFields> getSlimUTXOIndependentFields(List<SlimUTXO> utxos) {
        return utxos.stream().map(SlimUTXO::getIndependentFields).collect(toList());
    }

    public SlimTransaction toRegularTx() throws NoSuchAlgorithmException,
            InvalidKeySpecException, IOException {
        PublicKey og = CryptographicUtils.fromEncodedFormat(origin);
        PublicKey dest = CryptographicUtils.fromEncodedFormat(destination);
        return new SlimTransaction(og, dest, inputs, outputsDestination, outputsOrigin, originSignature);
    }

}
