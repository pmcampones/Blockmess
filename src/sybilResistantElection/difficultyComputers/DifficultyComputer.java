package sybilResistantElection.difficultyComputers;

public interface DifficultyComputer {

    int getSolutionLeadingZeros(byte[] solution);

    int getNumLeadingZeros();

    boolean hasEnoughLeadingZeros(byte[] solution);

}
