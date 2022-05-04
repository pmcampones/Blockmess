package sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers;

import java.util.Properties;

import static java.lang.Integer.parseInt;

public class LedgerGPoETDifficultyComputer implements GPoETDifficultyComputer {

    public static final String EXPECTED_NUM_NODES = "4";

    public static final String TIME_BETWEEN_QUERIES = "5000"; //5 seconds

    public static final String EXPECTED_TIME_BETWEEN_BLOCKS = "60000";

    protected final int numLeadingZeros;

    public LedgerGPoETDifficultyComputer(Properties props) {
        int expectedNumNodes = parseInt(props.getProperty("expectedNumNodes", EXPECTED_NUM_NODES));
        int timeBetweenQueries = parseInt(props.getProperty("timeBetweenQueries", TIME_BETWEEN_QUERIES));
        int expectedTimeBetweenBlocks = parseInt(props
                .getProperty("expectedTimeBetweenBlocks", EXPECTED_TIME_BETWEEN_BLOCKS));
        numLeadingZeros = computeDifficulty(expectedNumNodes, timeBetweenQueries, expectedTimeBetweenBlocks);
    }

    /**
     * Constructor should be used for tests.
     * Passing the parameters in the properties is the correct way to generate the correct number of leading zeros.
     */
    public LedgerGPoETDifficultyComputer(int numLeadingZeros) {
        this.numLeadingZeros = numLeadingZeros;
    }

    LedgerGPoETDifficultyComputer(double probNodeFindingSolutionInRound) {
        this.numLeadingZeros = computeNumLeadingZeros(probNodeFindingSolutionInRound);
    }

    /**
     *
     * @param expectedNumNodes The number of expected nodes in the system.
     *                         <p>Can be emulated to represent the number of queries to the hash random oracle,
     *                         per unit of time "timeBetweenQueries".</p>
     * @param timeBetweenQueries The interval of time a node is waiting between queries.
     * @param expectedTimeBetweenBlocks The expected time between blocks proposals following a geometric distribution
     *                                  with parameter (expectedNumNodes / timeBetweenQueries).
     * @return The number of leading zeros necessary to ensure that the expected time between block proposals is AT LEAST
     * the value given in the expectedTimeBetweenBlocks parameter.
     * <p>The number of zeros rounds up, and thus the time between blocks may increase a lot (nearly twice as long)
     * if the parameters chosen lead to an unfortunate rounding.</p>
     */
    private int computeDifficulty(int expectedNumNodes, int timeBetweenQueries, int expectedTimeBetweenBlocks) {
        double probNodeFindingSolutionInRound = getProbNodeFindingSolutionInRound(expectedNumNodes, timeBetweenQueries, expectedTimeBetweenBlocks);
        return computeNumLeadingZeros(probNodeFindingSolutionInRound);
    }

    private int computeNumLeadingZeros(double probNodeFindingSolutionInRound) {
        double leadingZeros = - Math.log(probNodeFindingSolutionInRound) / Math.log(2);
        return (int) Math.ceil(leadingZeros);
    }

    static double getProbNodeFindingSolutionInRound(int expectedNumNodes, int timeBetweenQueries, int expectedTimeBetweenBlocks) {
        int numRoundsBetweenBlocks = expectedTimeBetweenBlocks / timeBetweenQueries;
        double probFindSolutionInARound = 1 / (double) numRoundsBetweenBlocks;
        return probFindSolutionInARound / expectedNumNodes;
    }

    @Override
    public boolean hasEnoughLeadingZeros(byte[] solution) {
        int numBytesZero = numLeadingZeros / Byte.SIZE;
        int numBitsZero = numLeadingZeros % Byte.SIZE;
        if (numBitsZero == 0) return hasGivenEmptyBytes(solution, numBytesZero);
        else return hasEnoughLeadingZeros(solution, numBytesZero, numBitsZero);
    }

    private boolean hasEnoughLeadingZeros(byte[] solution, int numBytesZero, int numBitsZero) {
        return (solution.length >= numBytesZero + 1)
                && hasGivenEmptyBytes(solution, numBytesZero)
                && Byte.toUnsignedInt(solution[numBytesZero]) <= (1 << (Byte.SIZE - numBitsZero)) - 1;
    }

    private boolean hasGivenEmptyBytes(byte[] solution, int numBytesZero) {
        if (solution.length < numBytesZero)
            return false;
        for (int i = 0; i < numBytesZero; i++)
            if (solution[i] != 0)
                return false;
        return true;
    }

    @Override
    public int getSolutionLeadingZeros(byte[] solution) {
        int accum = 0;
        for (byte solByte : solution)
            if (solByte == 0)
                accum += Byte.SIZE;
            else {
                accum += computeZerosInByte(solByte);
                break;
            }
        return accum;
    }

    private int computeZerosInByte(byte solByte) {
        byte mask = (byte) (1 << Byte.SIZE - 1);
        int accum = 0;
        while ((solByte & mask) == 0) {
            accum++;
            mask = (byte) (mask >> 1);
        }
        return accum;
    }

    @Override
    public int getNumLeadingZeros() {
        return numLeadingZeros;
    }

}
