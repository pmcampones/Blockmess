package sybilResistantCommitteeElection.pos.sortition.proofs;

import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.UUID;

public interface LargeSortitionProof extends SortitionProof {

    int getRound();

    int getVotes();

    UUID getKeyBlockId();

    byte[] getHashProof();

}
