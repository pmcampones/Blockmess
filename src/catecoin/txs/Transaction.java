package catecoin.txs;

import catecoin.utxos.SlimUTXOIndependentFields;
import catecoin.utxos.UTXO;
import catecoin.validators.ContextObliviousValidator;
import io.netty.buffer.ByteBuf;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.CryptographicUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Transaction used to exchange coins between two blocks.
 * All collections in this class are unmodifiable, as any change in the instance renders the signature invalid.
 *  The same logic applies to ContentList.
 * However it assigns more responsibilities to the application validator class (by default {@link ContextObliviousValidator})
 */
public class Transaction extends ProtoPojoAbstract implements IndexableContent {

    private static final long serialVersionUID = 1L;

    public static final short ID = 1206;

    /**
     * Unique identifier of the Transaction.
     * Computed based on its fields, so that the Adversary is unable to create another transaction with the same id.
     * Is computed both by the node creating the Transaction as well as the nodes receiving them.
     */
    private transient final UUID txId;

    private transient final byte[] hashVal;

    private final PublicKey origin, destination;

    /**
     * Identifiers of the UTXO inputs used in the transaction.
     */
    private final List<UUID> inputs;

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo pojo, ByteBuf out) throws IOException {
            Transaction tx = (Transaction) pojo;
            CryptographicUtils.serializeKey(tx.origin, out);
            CryptographicUtils.serializeKey(tx.destination, out);
            serializeInputs(tx, out);
            serializeOutputs(tx.outputsDestination, out);
            serializeOutputs(tx.outputsOrigin, out);
            out.writeShort(tx.originSignature.length);
            out.writeBytes(tx.originSignature);
        }

        private void serializeInputs(Transaction tx, ByteBuf out) {
            out.writeShort(tx.inputs.size());
            for (UUID input : tx.inputs) {
                out.writeLong(input.getMostSignificantBits());
                out.writeLong(input.getLeastSignificantBits());
            }
        }

        private void serializeOutputs(List<UTXO> outputs, ByteBuf out) {
            out.writeShort(outputs.size());
            for (UTXO output : outputs) {
                out.writeInt(output.getNonce());
                out.writeInt(output.getAmount());
            }
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) throws IOException {
            PublicKey origin = CryptographicUtils.deserializePubKey(in);
            PublicKey destination = CryptographicUtils.deserializePubKey(in);
            List<UUID> inputs = deserializeInputs(in);
            List<SlimUTXOIndependentFields> outputsDestination = deserializeOutputs(in);
            List<SlimUTXOIndependentFields> outputsOrigin = deserializeOutputs(in);
            byte[] originSignature = new byte[in.readShort()];
            in.readBytes(originSignature);
            return new Transaction(origin, destination, Collections.unmodifiableList(inputs),
                    Collections.unmodifiableList(outputsDestination),
                    Collections.unmodifiableList(outputsOrigin),
                    originSignature);
        }

        private List<UUID> deserializeInputs(ByteBuf in) {
            return ProtoPojo.deserializeUuids(in);
        }

        private List<SlimUTXOIndependentFields> deserializeOutputs(ByteBuf in) {
            int numOutputs = in.readShort();
            List<SlimUTXOIndependentFields> outputs = new ArrayList<>(numOutputs);
            for (int i = 0; i < numOutputs; i++)
                outputs.add(new SlimUTXOIndependentFields(in.readInt(), in.readInt()));
            return outputs;
        }

    };
    /**
     * List of simplified UTXOs belonging to the destination of the transaction.
     */
    private final List<UTXO> outputsDestination;

    private final byte[] originSignature;

    /**
     * Auxiliary variable used to determine the size of the transaction.
     * This is used in the validation process of blocks in order to identify whether they exceed the max size.
     */
    private transient final int serializedSize;
    /**
     * List of simplified UTXOs belonging to the originator of the transaction.
     * These are used for the origin to obtain the excess UTXO coins sent in the input.
     */
    private final List<UTXO> outputsOrigin;

    /**
     * Constructor called by the originator of the transaction.
     * @param origin The identifier and signature verifier of the node issuing the transaction.
     * @param destination Identifier of the transaction destination.
     * @param inputs Identifiers of the UTXO inputs used in the transaction.
     * @param destinationAmounts List with the number of coins to be used in the UTXOs to the destination node.
     * @param originAmounts List with the number of coins to the used in the UTXOs to the origin node.
     * @param originSigner Private key of the origin used to sign the transaction. It is not sent through the network.
     * @throws IOException Exception thrown if the Keys received as arguments are somehow malformed.
     */
    public Transaction(PublicKey origin, PublicKey destination,
                       List<UUID> inputs, List<Integer> destinationAmounts,
                       List<Integer> originAmounts, PrivateKey originSigner)
            throws IOException, SignatureException, InvalidKeyException {
        super(ID);
        this.origin = origin;
        this.destination = destination;
        this.inputs = inputs;
        this.outputsDestination = computeOutputsAmount(destinationAmounts, destination);
        this.outputsOrigin = computeOutputsAmount(originAmounts, origin);
        this.hashVal = obtainTxByteFields();
        this.txId = CryptographicUtils.generateUUIDFromBytes(hashVal);
        this.serializedSize = hashVal.length +
                (UTXO.SERIALIZED_SIZE - 2 * Long.BYTES) * (outputsDestination.size() + outputsOrigin.size());
        this.originSignature = CryptographicUtils.getFieldsSignature(hashVal, originSigner);
    }

    private List<UTXO> computeOutputsAmount(List<Integer> amounts, PublicKey destination)
            throws IOException {
        List<UTXO> outputs = new ArrayList<>(amounts.size());
        for (Integer amount : amounts)
            outputs.add(new UTXO(amount, origin, destination));
        return outputs;
    }

    private byte[] obtainTxByteFields() throws IOException {
           try(var out = new ByteArrayOutputStream();
               var oout = new ObjectOutputStream(out)) {
               oout.writeObject(origin);
               oout.writeObject(destination);
               for(UUID input : inputs) oout.writeObject(input);
               for(UTXO outD : outputsDestination)
                   oout.writeObject(outD.getId());
                for(UTXO outO : outputsOrigin)
                    oout.writeObject(outO.getId());
                oout.flush();
                return out.toByteArray();
           }
    }

    /**
     * Constructor called by the nodes receiving this transaction.
     * @param origin The identifier and signature verifier of the node issuing the transaction.
     * @param destination Identifier of the transaction destination.
     * @param inputs Identifiers of the UTXO inputs used in the transaction.
     * @param destinationFields The UTXOs destined to the transaction target.
     * @param originFields The UTXOs destined to the issuer of the transaction. As a means to refund extra coins.
     * @param originSignature Signed content signed by the originator of the transaction. Is verified with the public key origin
     * @throws IOException Exception thrown if the Keys received as arguments are somehow malformed.
     */
    public Transaction(PublicKey origin, PublicKey destination,
                       List<UUID> inputs, List<SlimUTXOIndependentFields> destinationFields,
                       List<SlimUTXOIndependentFields> originFields, byte[] originSignature)
            throws IOException {
        super(ID);
        this.origin = origin;
        this.destination = destination;
        this.inputs = inputs;
        this.outputsDestination = computeOutputsFields(destinationFields, destination);
        this.outputsOrigin = computeOutputsFields(originFields, origin);
        this.originSignature = originSignature;
        this.hashVal = obtainTxByteFields();
        this.txId = CryptographicUtils.generateUUIDFromBytes(hashVal);
        this.serializedSize = hashVal.length +
                (UTXO.SERIALIZED_SIZE - 2 * Long.BYTES) * (destinationFields.size() + originFields.size());
    }

    @Override
    public UUID getId() {
        return txId;
    }

    @Override
    public byte[] getHashVal() {
        return hashVal;
    }

    public PublicKey getOrigin() {
        return origin;
    }

    public PublicKey getDestination() {
        return destination;
    }

    public List<UUID> getInputs() {
        return inputs;
    }

    private List<UTXO> computeOutputsFields(List<SlimUTXOIndependentFields> fields, PublicKey destination)
            throws IOException {
        List<UTXO> outputs = new ArrayList<>(fields.size());
        for (var field : fields)
            outputs.add(new UTXO(field, origin, destination));
        return outputs;
    }

    public List<UTXO> getOutputsDestination() {
        return outputsDestination;
    }

    @Override
    public int getSerializedSize() {
        return serializedSize;
    }

    /**
     * Verifies if a transaction's content in isolation is valid, that is, it is not malformed.
     * For a transaction to be semantically valid it must not be empty and all its UTXOs must be distinct.
     * At least one UTXO must be directed to the destination address.
     * Beyond testing the validity and correct use of the UTXOs,
     *  the signature of the transaction itself must be valid.
     *
     * This validation by itself is not sufficient to ensure the validity of the transaction.
     * The Adversary is capable of creating UTXOs with valid content and signatures,
     *  however these UTXOs may not have been recorded before.
     * As the method name implies, only the correctness of the transaction is ensured,
     *  disregarding its context in the system as a whole.
     * The "syntax"' validation is responsibility of the serialization.
     *  A transaction with incorrect syntax should not reach this stage.
     * By incorrect syntax we include the existence of null values.
     * The "pragmatics" validation is responsibility of protocols aware of the state of the system.
     * Further pragmatism is left to the economists and mechanism designers.
     *
     * NOTE: We allow one node to send a transaction to itself. Thus being able to exchange UTXO.
     *       We see no harm in this, however the system can work correctly if this was disallowed.
     *       Should this prove to be an attack vector, we should rectify this behaviour.
     */
    @Override
    public boolean hasValidSemantics() {
        return  !inputs.isEmpty() && !outputsDestination.isEmpty()
                && !hasInvalidAmount(outputsDestination)
                && !hasInvalidAmount(outputsOrigin)
                && ProtoPojo.allUnique(inputs.stream())
                && ProtoPojo.allUnique(outputsDestination.stream())
                && ProtoPojo.allUnique(outputsOrigin.stream())
                && hasValidSignature();
    }

    public List<UTXO> getOutputsOrigin() {
        return outputsOrigin;
    }

    private boolean hasValidSignature() {
        try {
            return CryptographicUtils.verifyPojoSignature(originSignature, obtainTxByteFields(), origin);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    private boolean hasInvalidAmount(List<UTXO> utxos) {
        return utxos.stream()
                .map(UTXO::getAmount)
                .anyMatch(amount -> amount <= 0);
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public UUID getBlockingID() {
        return txId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Transaction && txId.equals(((Transaction) other).txId);
    }
}
