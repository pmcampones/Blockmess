package valueDispatcher.requests;

import catecoin.nodeJoins.AutomatedNodeJoin;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import utils.IDGenerator;

public class DisseminateAutomatedNodeJoinRequest extends ProtoRequest {

    public static final short ID = IDGenerator.genId();

    private final AutomatedNodeJoin automatedNodeJoin;

    public DisseminateAutomatedNodeJoinRequest(AutomatedNodeJoin automatedNodeJoin) {
        super(ID);
        this.automatedNodeJoin = automatedNodeJoin;
    }

    public AutomatedNodeJoin getAutomatedNodeJoin() {
        return automatedNodeJoin;
    }

}
