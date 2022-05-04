package sybilResistantCommitteeElection.poet.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import utils.IDGenerator;

public class InitializationPeriodTimer extends ProtoTimer {

    public static final short ID = IDGenerator.genId();

    public InitializationPeriodTimer() {
        super(ID);
    }

    @Override
    public ProtoTimer clone() {
        return new InitializationPeriodTimer();
    }

}
