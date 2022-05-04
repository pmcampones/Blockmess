package sybilResistantCommitteeElection.pos.sortition.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import utils.IDGenerator;

public class WaitForNextElectionRoundTimer extends ProtoTimer {

    public static final short ID = IDGenerator.genId();

    public static final long WAIT_TIME = 5 * 1000;

    public static final long ERROR_WAIT = 60 * 1000;

    public WaitForNextElectionRoundTimer() {
        super(ID);
    }

    @Override
    public ProtoTimer clone() {
        return new WaitForNextElectionRoundTimer();
    }
}
