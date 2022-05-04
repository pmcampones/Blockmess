package catecoin.requests;

import sybilResistantCommitteeElection.SybilElectionProof;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

import java.util.Set;
import java.util.UUID;

public class BlockContentRequest<P extends SybilElectionProof> extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final Set<UUID> previousStates;

    private final P proof;

    public BlockContentRequest(Set<UUID> previousStates, P proof) {
        super(ID);
        this.previousStates = previousStates;
        this.proof = proof;
    }

    public Set<UUID> getPreviousStates() {
        return previousStates;
    }

    public P getSybilResistantProof() {
        return proof;
    }

}
