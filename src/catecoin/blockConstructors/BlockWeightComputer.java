package catecoin.blockConstructors;

import sybilResistantCommitteeElection.SybilElectionProof;

@FunctionalInterface
public interface BlockWeightComputer<P extends SybilElectionProof> {

    int DEFAULT_WEIGHT = 1;

    int computeBlockWeight (P proof);

}
