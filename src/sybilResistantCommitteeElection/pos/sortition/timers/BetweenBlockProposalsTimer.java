package sybilResistantCommitteeElection.pos.sortition.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import utils.IDGenerator;

public class BetweenBlockProposalsTimer extends ProtoTimer {

    public static final short ID = IDGenerator.genId();

    public static final long WAIT_PERIOD = 5000;

    public BetweenBlockProposalsTimer() {
        super(ID);
    }

    @Override
    public ProtoTimer clone() {
        return new BetweenBlockProposalsTimer();
    }

}
