package sybilResistantCommitteeElection;

import java.io.DataOutputStream;
import java.io.IOException;

public class DRandRound {

    private final int round;

    private final String randomness;

    private final String signature;

    private final String previous_signature;

    public DRandRound(int round, String randomness, String signature, String previous_signature) {
        this.round = round;
        this.randomness = randomness;
        this.signature = signature;
        this.previous_signature = previous_signature;
    }

    public int getRound() {
        return round;
    }

    public String getRandomnessStr() {
        return randomness;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DRandRound
                && this.round == ((DRandRound) other).round;
    }

    public void serialize (DataOutputStream out) throws IOException {
        out.writeInt(round);
        out.writeUTF(randomness);
        out.writeUTF(signature);
        out.writeUTF(previous_signature);
    }

}
