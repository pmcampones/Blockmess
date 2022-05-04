package intermediateConsensus.replies;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;
import utils.IDGenerator;

import java.util.Set;
import java.util.UUID;

public class GetBlockPreviousReply extends ProtoReply {

    public static final short ID = IDGenerator.genId();

    private final Set<UUID> previous;

    public GetBlockPreviousReply(Set<UUID> previous) {
        super(ID);
        this.previous = previous;
    }

}
