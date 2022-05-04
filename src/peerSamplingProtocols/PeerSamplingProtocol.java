package peerSamplingProtocols;


import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Set;

public interface PeerSamplingProtocol {

    /**
     * Returns the calling nodes' peers.
     * The peers are the nodes with which the calling node has a TCP connection open
     */
    Set<Host> getPeers();

    /**
     * Retrieves the identifier of the channel that connects the peers
     */
    int getChannelID();

}
