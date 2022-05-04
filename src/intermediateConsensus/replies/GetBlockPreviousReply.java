package intermediateConsensus.replies;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;
import utils.IDGenerator;

import java.util.Set;
import java.util.UUID;

public class GetBlockPreviousReply extends ProtoReply {

    public static final short ID = IDGenerator.genId();

    public GetBlockPreviousReply() {
        super(ID);
    }

}
