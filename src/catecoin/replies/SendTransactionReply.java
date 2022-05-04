package catecoin.replies;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;
import utils.IDGenerator;

import java.security.PublicKey;
import java.util.Set;
import java.util.UUID;

public class SendTransactionReply extends ProtoReply {

    public static final short ID = IDGenerator.genId();

    public SendTransactionReply() {
        super(ID);
    }

}
