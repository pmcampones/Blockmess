package sybilResistantElection.difficultyComputers;

import main.GlobalProperties;

import java.util.Properties;

import static java.lang.Integer.parseInt;
import static sybilResistantElection.difficultyComputers.BaseDifficultyComputer.*;

public class MultiChainDifficultyComputerImp implements MultiChainDifficultyComputer {

    private final double probNodeFindingSolutionInRound;

    private int numChains = 1;

    private DifficultyComputer inner;

    public MultiChainDifficultyComputerImp(int numChains) {
        Properties props = GlobalProperties.getProps();
        int expectedNumNodes = parseInt(props.getProperty("expectedNumNodes", EXPECTED_NUM_NODES));
        int timeBetweenQueries = parseInt(props.getProperty("timeBetweenQueries", TIME_BETWEEN_QUERIES));
        int expectedTimeBetweenBlocks = parseInt(props.getProperty("expectedTimeBetweenBlocks",
                EXPECTED_TIME_BETWEEN_BLOCKS));
        probNodeFindingSolutionInRound = BaseDifficultyComputer.getProbNodeFindingSolutionInRound(expectedNumNodes,
                timeBetweenQueries, expectedTimeBetweenBlocks);
        inner = new BaseDifficultyComputer(numChains * probNodeFindingSolutionInRound);
    }

    @Override
    public int getSolutionLeadingZeros(byte[] solution) {
        return inner.getSolutionLeadingZeros(solution);
    }

    @Override
    public int getNumLeadingZeros() {
        return inner.getNumLeadingZeros();
    }

    @Override
    public boolean hasEnoughLeadingZeros(byte[] solution) {
        return inner.hasEnoughLeadingZeros(solution);
    }

    @Override
    public void setNumChains(int Chains) {
        if (numChains != Chains) {
            numChains = Chains;
            inner = new BaseDifficultyComputer(
                    Chains * probNodeFindingSolutionInRound);
        }
    }

}
