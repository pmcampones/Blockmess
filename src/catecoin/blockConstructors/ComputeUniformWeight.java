package catecoin.blockConstructors;

import sybilResistantCommitteeElection.SybilElectionProof;

public class ComputeUniformWeight<P extends SybilElectionProof> implements BlockWeightComputer<P> {
    @Override
    public int computeBlockWeight() {
        return DEFAULT_WEIGHT;
    }
}
