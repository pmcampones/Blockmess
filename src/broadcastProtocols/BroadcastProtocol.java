package broadcastProtocols;

import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Set;
import java.util.UUID;

public interface BroadcastProtocol {

    Set<UUID> getMsgIds();

    Set<ProtoMessage> getMsgs();

    Set<Host> getPeers();

    void sendMessageToPeer(ProtoMessage msg, Host target);

    void deliverMessage(ProtoMessage msg);

}
