package catecoin.replies;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;
import utils.IDGenerator;

public class BalanceReply extends ProtoReply {

    public static final short ID = IDGenerator.genId();

    private final int balance;

    public BalanceReply(int balance) {
        super(ID);
        this.balance = balance;
    }

    public int getBalance() {
        return balance;
    }
}
