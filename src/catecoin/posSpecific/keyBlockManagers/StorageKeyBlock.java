package catecoin.posSpecific.keyBlockManagers;

import java.security.PublicKey;
import java.util.UUID;

public class StorageKeyBlock {

    private final PublicKey proposer;

    private final int cumulativeWeight;

    private final UUID prevRef;


    public StorageKeyBlock(PublicKey proposer, int cumulativeWeight, UUID prevRef) {
        this.proposer = proposer;
        this.cumulativeWeight = cumulativeWeight;
        this.prevRef = prevRef;
    }

    public PublicKey getProposer() {
        return proposer;
    }

    public int getCumulativeWeight() {
        return cumulativeWeight;
    }

    public UUID getPrevRef() {
        return prevRef;
    }
}
