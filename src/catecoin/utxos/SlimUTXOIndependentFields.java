package catecoin.utxos;

/**
 * Fields of a UTXO independent from their context in a Transaction.
 * Used to materialize the UTXOs when transactions arrive to a node through the broadcast protocols.
 */
public class SlimUTXOIndependentFields {

    private final int nonce;

    private final int amount;

    public SlimUTXOIndependentFields(int nonce, int amount) {
        this.nonce = nonce;
        this.amount = amount;
    }

    public int getNonce() {
        return nonce;
    }

    public int getAmount() {
        return amount;
    }
}
