package catecoin.blocks.chunks;

import sybilResistantCommitteeElection.SybilElectionProof;

import java.security.PublicKey;

/**
 * Mempool chunks where it's possible to retrieve the proof of the corresponding block.
 * <p>This proof may be necessary to validate other blocks.
 * A common need on validators that are not context oblivious.</p>
 */
public interface FutureProofChunks extends MempoolChunk {

    SybilElectionProof getProof();

}
