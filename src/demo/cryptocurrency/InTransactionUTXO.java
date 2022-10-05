package demo.cryptocurrency;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

/**
 * While aggregated within transactions, UTXOs do not carry their full content. The content not figured in this class;
 * the public keys of its origin and destination, are already present in the tx.
 */
@Getter
@AllArgsConstructor
public class InTransactionUTXO implements Serializable {

	private final int nonce, amount;

}
