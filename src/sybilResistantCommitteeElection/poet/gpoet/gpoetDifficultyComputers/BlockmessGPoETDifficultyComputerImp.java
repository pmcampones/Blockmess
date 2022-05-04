package sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers;

import java.util.Properties;

import static java.lang.Integer.parseInt;
import static sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers.LedgerGPoETDifficultyComputer.*;

public class BlockmessGPoETDifficultyComputerImp implements BlockmessGPoETDifficultyComputer {

    private final double probNodeFindingSolutionInRound;

    private int numChains = 1;

    private GPoETDifficultyComputer inner;

    public BlockmessGPoETDifficultyComputerImp(Properties props, int numChains) {
        int expectedNumNodes = parseInt(props.getProperty("expectedNumNodes", EXPECTED_NUM_NODES));
        int timeBetweenQueries = parseInt(props.getProperty("timeBetweenQueries", TIME_BETWEEN_QUERIES));
        int expectedTimeBetweenBlocks = parseInt(props.getProperty("expectedTimeBetweenBlocks",
                EXPECTED_TIME_BETWEEN_BLOCKS));
        probNodeFindingSolutionInRound = LedgerGPoETDifficultyComputer.getProbNodeFindingSolutionInRound(expectedNumNodes,
                timeBetweenQueries, expectedTimeBetweenBlocks);
        inner = new LedgerGPoETDifficultyComputer(numChains * probNodeFindingSolutionInRound);
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
            inner = new LedgerGPoETDifficultyComputer(
                    Chains * probNodeFindingSolutionInRound);
        }
    }

}
