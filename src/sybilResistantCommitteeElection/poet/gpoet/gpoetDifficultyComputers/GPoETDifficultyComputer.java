package sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers;

public interface GPoETDifficultyComputer {

    int getSolutionLeadingZeros(byte[] solution);

    int getNumLeadingZeros();

    boolean hasEnoughLeadingZeros(byte[] solution);

}
