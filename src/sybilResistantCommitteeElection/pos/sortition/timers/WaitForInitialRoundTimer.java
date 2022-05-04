package sybilResistantCommitteeElection.pos.sortition.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import utils.IDGenerator;

public class WaitForInitialRoundTimer extends ProtoTimer {

    public static final short ID = IDGenerator.genId();

    public WaitForInitialRoundTimer() {
        super(ID);
    }

    @Override
    public ProtoTimer clone() {
        return new WaitForInitialRoundTimer();
    }
}
