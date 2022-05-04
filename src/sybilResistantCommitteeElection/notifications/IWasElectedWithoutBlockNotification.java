package sybilResistantCommitteeElection.notifications;

import sybilResistantCommitteeElection.SybilElectionProof;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import utils.IDGenerator;

import java.security.PublicKey;
import java.util.Set;

public class IWasElectedWithoutBlockNotification<P extends SybilElectionProof> extends ProtoNotification {

    public static final short ID = IDGenerator.genId();

    public final Set<PublicKey> committee;
    
    public final P proof;

    public IWasElectedWithoutBlockNotification(Set<PublicKey> committee, P proof) {
        super(ID);
        this.committee = committee;
        this.proof = proof;
    }

}