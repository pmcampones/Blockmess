package peerSamplingProtocols.fullMembership;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peerSamplingProtocols.PeerSamplingProtocol;
import peerSamplingProtocols.fullMembership.messages.SampleMessage;
import peerSamplingProtocols.fullMembership.timers.SampleTimer;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.IDGenerator;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class SimpleFullMembership extends GenericProtocol implements PeerSamplingProtocol {
    private static final Logger logger = LogManager.getLogger(SimpleFullMembership.class);

    private final Host self;     //My own address/port
    private final Set<Host> membership = new HashSet<>(); //Peers I am connected to
    private final Set<Host> pending = new HashSet<>(); //Peers I am trying to connect to
    private final int subsetSize; //param: maximum size of sample;
    private final Random rnd = new Random();
    private final int channelId;

    public SimpleFullMembership(Properties props, Host self) throws IOException, HandlerRegistrationException {
        super(SimpleFullMembership.class.getSimpleName(), IDGenerator.genId());
        this.self = self;
        this.channelId = createChannel(props); //Create the channel with the given properties
        this.subsetSize = Integer.parseInt(props.getProperty("sample_size", "6"));
        channelConfigurations();
    }

    private int createChannel(Properties props) throws IOException {
        //String cMetricsInterval = props.getProperty("channel_metrics_interval", "10000"); //10 seconds
        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, props.getProperty("address")); //The address to bind to
        channelProps.setProperty(TCPChannel.PORT_KEY, props.getProperty("port")); //The port to bind to
        //channelProps.setProperty(TCPChannel.METRICS_INTERVAL_KEY, cMetricsInterval); //The interval to receive channel metrics
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "2000"); //Heartbeats interval for established connections
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "5000"); //Time passed without heartbeats until closing a connection
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "2000"); //TCP connect timeout
        return createChannel(TCPChannel.NAME, channelProps);
    }

    private void channelConfigurations() throws HandlerRegistrationException {
        registerMessageSerializer(channelId, SampleMessage.ID, SampleMessage.serializer);
        registerMessageHandler(channelId, SampleMessage.ID, (SampleMessage msg1, Host from, short sourceProto, int channelId2) -> uponSample(msg1, from), (msg, host, destProto, throwable, channelId1) -> uponMsgFail(msg, host, throwable));
        registerChannelEvents();
    }

    private void registerChannelEvents() throws HandlerRegistrationException {
        registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, (OutConnectionDown event3, int channelId4) -> uponOutConnectionDown(event3));
        registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, (OutConnectionFailed<ProtoMessage> event2, int channelId3) -> uponOutConnectionFailed(event2));
        registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, (OutConnectionUp event2, int channelId3) -> uponOutConnectionUp(event2));
        registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, (InConnectionUp event1, int channelId2) -> uponInConnectionUp(event1));
        registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, (InConnectionDown event, int channelId1) -> uponInConnectionDown(event));
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {
        establishContact(props);
        configureSampling(props);
    }

    private void establishContact(Properties props) {
        if (props.containsKey("contact")) {
            try {
                String contact = props.getProperty("contact");
                String[] hostElems = contact.split(":");
                Host contactHost = new Host(InetAddress.getByName(hostElems[0]), Short.parseShort(hostElems[1]));
                //We add to the pending set until the connection is successful
                pending.add(contactHost);
                openConnection(contactHost);
            } catch (Exception e) {
                logger.error("Invalid contact on configuration: '" + props.getProperty("contacts"));
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    private void configureSampling(Properties props) throws HandlerRegistrationException {
        registerTimerHandler(SampleTimer.ID, (SampleTimer timer, long timerId) -> uponSampleTimer());
        int sampleTime = Integer.parseInt(props.getProperty("sample_time", "2000")); //2 seconds
        setupPeriodicTimer(new SampleTimer(), sampleTime, sampleTime);
    }

    /*--------------------------------- Messages ---------------------------------------- */
    private void uponSample(SampleMessage msg, Host from) {
        logger.debug("Received {} from {}", msg, from);
        for (Host h : msg.sample) {
            if (!h.equals(self) && !membership.contains(h) && !pending.contains(h)) {
                pending.add(h);
                openConnection(h);
            }
        }
    }

    private void uponMsgFail(ProtoMessage msg, Host host,
                             Throwable throwable) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    /*--------------------------------- Timers ---------------------------------------- */
    private void uponSampleTimer() {
        //When the SampleTimer is triggered, get a random peer in the membership and send a sample
        logger.debug("Sample Time: membership{}", membership);
        if (membership.size() > 0) {
            Host target = getRandom(membership);
            Set<Host> subset = getRandomSubsetExcluding(membership, subsetSize, target);
            subset.add(self);
            sendMessage(new SampleMessage(subset), target);
            logger.debug("Sent SampleMessage {}", target);
        }
    }

    //Gets a random element from the set of peers
    private Host getRandom(Set<Host> hostSet) {
        int idx = rnd.nextInt(hostSet.size());
        return new ArrayList<>(membership).get(idx);
    }

    //Gets a random subset from the set of peers
    private static Set<Host> getRandomSubsetExcluding(Set<Host> hostSet, int sampleSize, Host exclude) {
        List<Host> list = new LinkedList<>(hostSet);
        list.remove(exclude);
        Collections.shuffle(list);
        return new HashSet<>(list.subList(0, Math.min(sampleSize, list.size())));
    }

    /* --------------------------------- TCPChannel Events ---------------------------- */

    //If a connection is successfully established, this event is triggered. In this protocol, we want to add the
    //respective peer to the membership, and inform the Dissemination protocol via a notification.
    private void uponOutConnectionUp(OutConnectionUp event) {
        Host peer = event.getNode();
        logger.debug("Connection to {} is up", peer);
        pending.remove(peer);
        membership.add(peer);
    }

    //If an established connection is disconnected, remove the peer from the membership and inform the Dissemination
    //protocol. Alternatively, we could do smarter things like retrying the connection X times.
    private void uponOutConnectionDown(OutConnectionDown event) {
        Host peer = event.getNode();
        logger.debug("Connection to {} is down cause {}", peer, event.getCause());
        membership.remove(event.getNode());
    }

    //If a connection fails to be established, this event is triggered. In this protocol, we simply remove from the
    //pending set. Note that this event is only triggered while attempting a connection, not after connection.
    //Thus the peer will be in the pending set, and not in the membership (unless something is very wrong with our code)
    private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> event) {
        logger.debug("Connection to {} failed cause: {}", event.getNode(), event.getCause());
        pending.remove(event.getNode());
    }

    //If someone established a connection to me, this event is triggered. In this protocol we do nothing with this event.
    //If we want to add the peer to the membership, we will establish our own outgoing connection.
    // (not the smartest protocol, but its simple)
    private void uponInConnectionUp(InConnectionUp event) {
        logger.trace("Connection from {} is up", event.getNode());
    }

    //A connection someone established to me is disconnected.
    private void uponInConnectionDown(InConnectionDown event) {
        logger.trace("Connection from {} is down, cause: {}", event.getNode(), event.getCause());
    }

    /* --------------------------------- Metrics ---------------------------- */

    @Override
    public Set<Host> getPeers() {
        return membership;
    }

    @Override
    public int getChannelID() {
        return channelId;
    }
}
