package valueDispatcher.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import sybilResistantCommitteeElection.pos.sortition.proofs.InElectionSortitionProof;
import utils.IDGenerator;

public class DeliverSortitionProofNotification extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    private final InElectionSortitionProof proof;

    public DeliverSortitionProofNotification(InElectionSortitionProof proof) {
        super(ID);
        this.proof = proof;
    }

    public InElectionSortitionProof getProof() {
        return proof;
    }

}
