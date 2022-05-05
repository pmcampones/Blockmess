package ledger.blocks;

import catecoin.blocks.ContentList;
import catecoin.blocks.ValidatorSignature;
import main.ProtoPojo;

import java.security.PublicKey;
import java.util.List;
import java.util.UUID;

/**
 * Generic block used in the Ledger protocol of a Distributed Ledger.
 * It is slightly more general than what is specified in <a href=https://bitcoin.org/bitcoin.pdf>Bitcoin</a>
 * for their use in Blockchains.
 * @param <C> The type of the content used in the application running the Distributed Ledger.
 * @param <P> The type of the SybilElectionProof used in these Blocks.
 */
public interface LedgerBlock<C extends ContentList<?>, P extends ProtoPojo & SizeAccountable>
        extends SizeAccountable, ProtoPojo {

    /**
     * Used as the hash of the block in Bitcoin.
     * Only has half the length (128 bits) but is easier to work with.
     */
    UUID getBlockId();

    /**
     * Inherent weight given to this block.
     * Used by some distributed ledgers where some blocks have a greater weight.
     * <p>One such example is <a href=https://www.usenix.org/system/files/conference/nsdi16/nsdi16-paper-eyal.pdf>Bitcoin-NG</a>,
     * where the finalization of blocks is the same as <a href=https://bitcoin.org/bitcoin.pdf>Bitcoin</a>
     * if we consider that the Key blocks have a greater weight than the Micro Blocks.</p>
     */
    int getInherentWeight();

    /**
     * The previous blocks this one references.
     * While in a blockchain each block only references one previous block,
     * DAG based DLs have blocks referencing several previous blocks.
     * <p>While the order of these previous references is not useful in terms of their manipulation
     * in the program, these must be uniquely ordered so any node can compute the same hash value for the block.</p>
     * @see <a href=https://fc15.ifca.ai/preproceedings/paper_101.pdf>Inclusive</a>,
     *  <a href=https://www.usenix.org/conference/atc20/presentation/li-chenxing>Conflux</a>,
     *  <a href=https://assets.ctfassets.net/r1dr6vzfxhev/2t4uxvsIqk0EUau6g2sw0g/45eae33637ca92f85dd9f4a3a218e1ec/iota1_4_3.pdf>Tangle</a>,
     *  <a href=https://content.nano.org/whitepaper/Nano_Whitepaper_en.pdf>Nano</a>
     */
    List<UUID> getPrevRefs();

    /**
     * Retrieves the content of the block.
     * <p>This content needs to be serializable in order to allow us to compute its hash without
     * needing to know the specifics of its implementation.</p>
     */
    C getContentList();

    /**
     * Retrieves the proof that the Sybil election was performed correctly.
     * <p>Validation of the proof rests on the Application protocols,
     * as such the Ledger protocol has no information on the internal structure of the proof.</p>
     */
    P getSybilElectionProof();

    /**
     * Retrieves the signatures of the validators of this block.
     * <p>In DLs with a single leader, the validator is comprised by the block proposer.</p>
     * <p>On DLs using BFT committees to select blocks this list will comprise of the validators in the committee.</p>
     * <p>Because different DLs may parameterize the validation differently,
     * it is a responsibility that rests on the application protocols.
     * For example, the value of allowed failures <b>F</b> may vary as long as it remains
     * bellow one third of the cardinality of the committee, as stated in
     * <a href=http://pmg.csail.mit.edu/papers/osdi99.pdf>PBFT</a>;
     * however, other, more lenient, approaches may allow more than <b>F</b> failures,
     * as seen in <a href=https://www.usenix.org/conference/nsdi-07/beyond-one-third-faulty-replicas-byzantine-fault-tolerant-systems>BFT2F</a></p>
     */
    List<ValidatorSignature> getSignatures();

    /**
     * Adds a new validator signature to the block.
     * <p>It is assumed that the validator signatures added come from validators in the current committee election
     * and that their signatures are valid.</p>
     * @throws UnsupportedOperationException If the block arrives by deserialization it's not expected that a new
     * validator will be added, and thus the implementation <i>should</i> throw this exception.
     */
    void addValidatorSignature(ValidatorSignature validatorSignature);

    /**
     * Retrieves the proposer of the block.
     */
    PublicKey getProposer();

    /**
     * Verifies whether the inherent properties of the block are valid.
     * <p>Perhaps this functionality should be externalized to the validator that will check these blocks.
     * Nevertheless, it is here for now.</p>
     */
    boolean hasValidSemantics();

}
