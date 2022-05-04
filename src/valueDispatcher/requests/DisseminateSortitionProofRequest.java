package valueDispatcher.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import sybilResistantCommitteeElection.pos.sortition.proofs.InElectionSortitionProof;
import utils.IDGenerator;

public class DisseminateSortitionProofRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final InElectionSortitionProof proof;

    public DisseminateSortitionProofRequest(InElectionSortitionProof proof) {
        super(ID);
        this.proof = proof;
    }

    public InElectionSortitionProof getProof() {
        return proof;
    }
}
