package catecoin.validators;

import sybilResistantCommitteeElection.SybilElectionProof;

import java.security.PublicKey;

public interface SybilProofValidator<P extends SybilElectionProof> {

    boolean isValid(P proof, PublicKey proposer);

}
