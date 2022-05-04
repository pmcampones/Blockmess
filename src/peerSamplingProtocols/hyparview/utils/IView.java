package peerSamplingProtocols.hyparview.utils;

import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Set;

public interface IView {

    void setOther(IView other, Set<Host> pending);

    Host addPeer(Host peer);

    boolean removePeer(Host peer);

    boolean containsPeer(Host peer);

    Host dropRandom();

    Set<Host> getRandomSample(int sampleSize);

    Set<Host> getPeers();

    Host getRandom();

    Host getRandomDiff(Host from);

    boolean fullWithPending(Set<Host> pending);

    boolean isFull();

    boolean isEmpty();
}
