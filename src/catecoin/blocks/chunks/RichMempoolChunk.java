package catecoin.blocks.chunks;

import catecoin.utxos.StorageUTXO;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.security.PublicKey;
import java.util.Set;
import java.util.UUID;

public class RichMempoolChunk implements FixedWeightChunk, FutureProofChunks {

    private final MinimalistMempoolChunk innerChunk;

    private final int cumulativeWeight;

    private final PublicKey proposer;

    private final SybilElectionProof proof;

    public RichMempoolChunk(MinimalistMempoolChunk innerChunk, int cumulativeWeight,
                            PublicKey proposer, SybilElectionProof proof) {
        this.innerChunk = innerChunk;
        this.cumulativeWeight = cumulativeWeight;
        this.proposer = proposer;
        this.proof = proof;
    }

    @Override
    public UUID getId() {
        return innerChunk.getId();
    }

    @Override
    public Set<UUID> getPreviousChunksIds() {
        return innerChunk.getPreviousChunksIds();
    }

    @Override
    public Set<UUID> getRemovedUtxos() {
        return innerChunk.getRemovedUtxos();
    }

    @Override
    public Set<UUID> getUsedTxs() {
        return innerChunk.getUsedTxs();
    }

    @Override
    public Set<StorageUTXO> getAddedUtxos() {
        return innerChunk.getAddedUtxos();
    }

    @Override
    public int getInherentWeight() {
        return innerChunk.getInherentWeight();
    }

    public PublicKey getProposer() {
        return proposer;
    }

    @Override
    public int getCumulativeWeight() {
        return cumulativeWeight;
    }

    @Override
    public SybilElectionProof getProof() {
        return proof;
    }
}
