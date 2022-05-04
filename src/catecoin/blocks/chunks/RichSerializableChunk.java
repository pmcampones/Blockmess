package catecoin.blocks.chunks;

import catecoin.utxos.JsonAcceptedUTXO;
import main.CryptographicUtils;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RichSerializableChunk implements SerializableChunk {

    private final UUID stateID;

    private final Set<UUID> previous;

    private final Map<UUID, JsonAcceptedUTXO> addedUtxos;

    private final Set<UUID> removedUtxos;

    private final Set<UUID> usedTxs;

    private final int inherantWeight;

    private final int cumulativeWeight;

    private final byte[] proposerEncoded;

    private final SybilElectionProof proof;

    public RichSerializableChunk(RichMempoolChunk chunk) {
        this.stateID = chunk.getId();
        this.previous = chunk.getPreviousChunksIds();
        this.addedUtxos = SerializableChunk.convertSerializableFormat(chunk.getAddedUtxos());
        this.removedUtxos = chunk.getRemovedUtxos();
        this.usedTxs = chunk.getUsedTxs();
        this.inherantWeight = chunk.getInherentWeight();
        this.cumulativeWeight = chunk.getCumulativeWeight();
        this.proposerEncoded = chunk.getProposer().getEncoded();
        this.proof = chunk.getProof();
    }

    @Override
    public MempoolChunk fromSerializableChunk()
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        MinimalistMempoolChunk min = new MinimalistMempoolChunk(stateID, previous,
                SerializableChunk.convertStorageFormat(addedUtxos), removedUtxos, usedTxs, inherantWeight);
        return new RichMempoolChunk(min, cumulativeWeight,
                CryptographicUtils.fromEncodedFormat(proposerEncoded), proof);
    }
}
