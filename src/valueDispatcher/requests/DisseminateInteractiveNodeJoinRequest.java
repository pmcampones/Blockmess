package valueDispatcher.requests;

import catecoin.nodeJoins.InteractiveNodeJoin;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class DisseminateInteractiveNodeJoinRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final InteractiveNodeJoin interactiveNodeJoin;

    public DisseminateInteractiveNodeJoinRequest(InteractiveNodeJoin interactiveNodeJoin) {
        super(ID);
        this.interactiveNodeJoin = interactiveNodeJoin;
    }

    public InteractiveNodeJoin getInteractiveNodeJoin() {
        return interactiveNodeJoin;
    }
}
