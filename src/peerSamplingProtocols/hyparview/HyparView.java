package peerSamplingProtocols.hyparview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peerSamplingProtocols.PeerSamplingProtocol;
import peerSamplingProtocols.hyparview.messages.*;
import peerSamplingProtocols.hyparview.notifications.NeighbourDownNotification;
import peerSamplingProtocols.hyparview.notifications.NeighbourUpNotification;
import peerSamplingProtocols.hyparview.timers.HelloTimer;
import peerSamplingProtocols.hyparview.timers.JoinTimer;
import peerSamplingProtocols.hyparview.timers.ShuffleTimer;
import peerSamplingProtocols.hyparview.utils.IView;
import peerSamplingProtocols.hyparview.utils.View;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;


public class HyparView extends GenericProtocol implements PeerSamplingProtocol  {

    private static final Logger logger = LogManager.getLogger(HyparView.class);

    public final static short PROTOCOL_ID = 4007;
    public final static String PROTOCOL_NAME = "HyParView";
    private static final int MAX_BACKOFF = 60000;

    private final short ARWL; //param: active random walk length
    private final short PRWL; //param: passive random walk length

    private final short shuffleTime; //param: timeout for shuffle
    private final short originalTimeout; //param: timeout for hello msgs
    private short timeout;

    private final short kActive; //param: number of active nodes to exchange on shuffle
    private final short kPassive; //param: number of passive nodes to exchange on shuffle


    protected final int channelId;
    protected final Host myself;

    protected IView active;
    protected IView passive;

    protected Set<Host> pending;
    private final Map<Short, Host[]> activeShuffles;

    private short seqNum = 0;

    protected final Random rnd;

    private final short joinTimeout; //param: timeout for retry join

    public HyparView(Properties props, Host myself) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.myself = myself;

        this.ARWL = Short.parseShort(props.getProperty("ARWL", "4")); //param: active random walk length
        this.PRWL = Short.parseShort(props.getProperty("PRWL", "2")); //param: passive random walk length

        this.shuffleTime = Short.parseShort(props.getProperty("shuffleTime", "2000")); //param: timeout for shuffle
        this.timeout = this.originalTimeout = Short.parseShort(props.getProperty("helloBackoff", "1000")); //param: timeout for hello msgs
        this.joinTimeout = Short.parseShort(props.getProperty("joinTimeout", "2000")); //param: timeout for retry join

        this.kActive = Short.parseShort(props.getProperty("kActive", "2")); //param: number of active nodes to exchange on shuffle
        this.kPassive = Short.parseShort(props.getProperty("kPassive", "3")); //param: number of passive nodes to exchange on shuffle

        this.rnd = new Random();
        int maxActive = Integer.parseInt(props.getProperty("ActiveView", "4")); //param: maximum active nodes (degree of random overlay)
        this.active = new View(maxActive, myself, rnd);
        int maxPassive = Integer.parseInt(props.getProperty("PassiveView", "7")); //param: maximum passive nodes
        this.passive = new View(maxPassive, myself, rnd);

        this.pending = new HashSet<>();
        this.activeShuffles = new TreeMap<>();

        this.active.setOther(passive, pending);
        this.passive.setOther(active, pending);

        this.channelId = createTCPChannel(props);
        registerMessageSerializers();
        registerMessageHandlers();
        registerTimerHandlers();
        registerChannelEvents();
    }

    private int createTCPChannel(Properties props) throws IOException {
        // Create a properties object to setup channel-specific properties. See the
        // channel description for more details.
        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, props.getProperty("address")); // The address to bind to
        channelProps.setProperty(TCPChannel.PORT_KEY, props.getProperty("port")); // The port to bind to
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); // Heartbeats interval for established
        // connections
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); // Time passed without heartbeats until
        // closing a connection
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); // TCP connect timeout
        int channelId = createChannel(TCPChannel.NAME, channelProps); // Create the channel with the given properties
        logger.info("Using channel {} for the broadcast protocol.", channelId);
        return channelId;
    }

    private void registerMessageSerializers() {
        registerMessageSerializer(channelId, JoinMessage.MSG_CODE, JoinMessage.serializer);
        registerMessageSerializer(channelId, JoinReplyMessage.MSG_CODE, JoinReplyMessage.serializer);
        registerMessageSerializer(channelId, ForwardJoinMessage.MSG_CODE, ForwardJoinMessage.serializer);
        registerMessageSerializer(channelId, HelloMessage.MSG_CODE, HelloMessage.serializer);
        registerMessageSerializer(channelId, HelloReplyMessage.MSG_CODE, HelloReplyMessage.serializer);
        registerMessageSerializer(channelId, DisconnectMessage.MSG_CODE, DisconnectMessage.serializer);
        registerMessageSerializer(channelId, ShuffleMessage.MSG_CODE, ShuffleMessage.serializer);
        registerMessageSerializer(channelId, ShuffleReplyMessage.MSG_CODE, ShuffleReplyMessage.serializer);
    }

    private void registerMessageHandlers() throws HandlerRegistrationException {
        registerMessageHandler(channelId, JoinMessage.MSG_CODE, this::uponReceiveJoin);
        registerMessageHandler(channelId, JoinReplyMessage.MSG_CODE, this::uponReceiveJoinReply);
        registerMessageHandler(channelId, ForwardJoinMessage.MSG_CODE, this::uponReceiveForwardJoin);
        registerMessageHandler(channelId, HelloMessage.MSG_CODE, this::uponReceiveHello);
        registerMessageHandler(channelId, HelloReplyMessage.MSG_CODE, this::uponReceiveHelloReply);
        registerMessageHandler(channelId, DisconnectMessage.MSG_CODE,
                this::uponReceiveDisconnect, this::uponDisconnectSent);
        registerMessageHandler(channelId, ShuffleMessage.MSG_CODE, this::uponReceiveShuffle);
        registerMessageHandler(channelId, ShuffleReplyMessage.MSG_CODE,
                this::uponReceiveShuffleReply, this::uponShuffleReplySent);
    }

    private void registerTimerHandlers() throws HandlerRegistrationException {
        registerTimerHandler(ShuffleTimer.ID, this::uponShuffleTimeout);
        registerTimerHandler(HelloTimer.ID, this::uponHelloTimeout);
        registerTimerHandler(JoinTimer.ID, this::uponJoinTimeout);
    }

    private void registerChannelEvents() throws HandlerRegistrationException {
        registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
        registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
        registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
        registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);
    }

    /*--------------------------------- Messages ---------------------------------------- */
    protected void handleDropFromActive(Host dropped) {
        if(dropped != null) {
            triggerNotification(new NeighbourDownNotification(dropped));
            sendMessage(new DisconnectMessage(), dropped);
            logger.debug("Sent DisconnectMessage to {}", dropped);
            passive.addPeer(dropped);
            logger.trace("Added to {} passive{}", dropped, passive);
        }
    }

    private void uponReceiveJoin(JoinMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        addHostToActiveAndReplyToJoin(from);
        for(Host peer : active.getPeers()) {
            if(!peer.equals(from)) {
                sendMessage(new ForwardJoinMessage(ARWL, from), peer);
                logger.debug("Sent ForwardJoinMessage to {}", peer);
            }
        }
    }

    private void uponReceiveJoinReply(JoinReplyMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        if(!active.containsPeer(from)) {
            passive.removePeer(from);
            pending.remove(from);
            openConnection(from);
            Host h = active.addPeer(from);
            logger.trace("Added to {} active{}", from, active);
            triggerNotification(new NeighbourUpNotification(from));
            handleDropFromActive(h);
        }
    }

    private void uponReceiveForwardJoin(ForwardJoinMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        Host newHost = msg.getNewHost();
        if(msg.getTtl() == 0 || active.getPeers().size() == 1) {
            if(!newHost.equals(myself) && !active.containsPeer(newHost)) {
                passive.removePeer(newHost);
                pending.remove(newHost);
                addHostToActiveAndReplyToJoin(newHost);
            }
        } else {
            if(msg.decrementTtl() == PRWL)  {
                passive.addPeer(newHost);
                logger.trace("Added to {} passive {}", newHost, passive);
            }
            Host next = active.getRandomDiff(from);
            if(next != null) {
                sendMessage(msg, next);
                logger.debug("Sent ForwardJoinMessage to {}", next);
            }
        }
    }

    private void uponReceiveHello(HelloMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        openConnection(from);

        if(msg.isPriority()) {
            sendMessage(new HelloReplyMessage(true), from);
            logger.debug("Sent HelloReplyMessage to {}", from);
            if(!active.containsPeer(from)) {
                pending.remove(from);
                logger.trace("Removed from {} pending{}", from, pending);
                passive.removePeer(from);
                logger.trace("Removed from {} passive{}", from, passive);
                Host h = active.addPeer(from);
                logger.trace("Added to {} active{}", from, active);
                triggerNotification(new NeighbourUpNotification(from));
                handleDropFromActive(h);
            }

        } else {
            pending.remove(from);
            logger.trace("Removed from {} pending{}", from, pending);
            if(!active.fullWithPending(pending) || active.containsPeer(from)) {
                sendMessage(new HelloReplyMessage(true), from);
                logger.debug("Sent HelloReplyMessage to {}", from);
                if(!active.containsPeer(from)) {
                    passive.removePeer(from);
                    logger.trace("Removed from {} passive{}", from, passive);
                    active.addPeer(from);
                    logger.trace("Added to {} active{}", from, active);
                    triggerNotification(new NeighbourUpNotification(from));
                }

            } else {
                sendMessage(new HelloReplyMessage(false), from, TCPChannel.CONNECTION_IN);
                logger.debug("Sent HelloReplyMessage to {}", from);
            }
        }
    }

    private void uponReceiveHelloReply(HelloReplyMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        pending.remove(from);
        logger.trace("Removed from {} pending{}", from, pending);
        if(msg.isTrue()) {
            if(!active.containsPeer(from)) {
                timeout = originalTimeout;
                openConnection(from);
                Host h = active.addPeer(from);
                logger.trace("Added to {} active{}", from, active);
                triggerNotification(new NeighbourUpNotification(from));
                handleDropFromActive(h);
            }
        } else if(!active.containsPeer(from)){
            passive.addPeer(from);
            closeConnection(from);
            logger.trace("Added to {} passive{}", from, passive);
            if(!active.fullWithPending(pending)) {
                setupTimer(new HelloTimer(), timeout);
            }
        }
    }

    protected void uponReceiveDisconnect(DisconnectMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        if(active.containsPeer(from)) {
            active.removePeer(from);
            logger.debug("Removed from {} active{}", from, active);
            handleDropFromActive(from);

            if(active.getPeers().isEmpty()) {
                timeout = originalTimeout;
            }

            if(!active.fullWithPending(pending)) {
                setupTimer(new HelloTimer(), timeout);
            }
        }
    }

    private void uponDisconnectSent(DisconnectMessage msg, Host host, short destProto, int channelId) {
        logger.trace("Sent {} to {}", msg, host);
        closeConnection(host);
    }

    private void uponReceiveShuffle(ShuffleMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        openConnection(from);
        msg.decrementTtl();
        if(msg.getTtl() > 0 && active.getPeers().size() > 1) {
            Host next = active.getRandomDiff(from);
            sendMessage(msg, next);
            logger.debug("Sent ShuffleMessage to {}", next);
        } else if(!msg.getOrigin().equals(myself)) {
            logger.trace("Processing {}, passive{}", msg, passive);
            Set<Host> peers = new HashSet<>(active.getRandomSample(msg.getFullSample().size()));
            Host[] hosts = peers.toArray(new Host[0]);
            int i = 0;
            for (Host host : msg.getFullSample()) {
                if (!host.equals(myself) && !active.containsPeer(host) && passive.isFull() && i < peers.size()) {
                    passive.removePeer(hosts[i]);
                    i++;
                }
                passive.addPeer(host);
            }
            logger.trace("After Passive{}", passive);
            sendMessage(new ShuffleReplyMessage(peers, msg.getSeqnum()), msg.getOrigin());
            logger.debug("Sent ShuffleReplyMessage to {}", msg.getOrigin());
        } else
            activeShuffles.remove(msg.getSeqnum());
    }

    private void uponShuffleReplySent(ShuffleReplyMessage msg, Host host, short destProto, int channelId) {
        if(!active.containsPeer(host) && !pending.contains(host)) {
            logger.trace("Disconnecting from {} after shuffleReply", host);
            closeConnection(host);
        }
    }

    private void uponReceiveShuffleReply(ShuffleReplyMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received {} from {}", msg, from);
        Host[] sent = activeShuffles.remove(msg.getSeqnum());
        List<Host> sample = msg.getSample();
        sample.add(from);
        int i = 0;
        logger.trace("Processing {}, passive{}", msg, passive);
        for (Host h : sample) {
            if(!h.equals(myself) && !active.containsPeer(h) && passive.isFull() && i < sent.length) {
                passive.removePeer(sent[i]);
                i ++;
            }
            passive.addPeer(h);
        }
        logger.trace("After Passive{}", passive);
    }


    /*--------------------------------- Timers ---------------------------------------- */

    private void uponShuffleTimeout(ShuffleTimer timer, long timerId) {
        if(!active.fullWithPending(pending)){
            setupTimer(new HelloTimer(), timeout);
        }

        Host h = active.getRandom();

        if(h != null) {
            Set<Host> peers = new HashSet<>();
            peers.addAll(active.getRandomSample(kActive));
            peers.addAll(passive.getRandomSample(kPassive));
            activeShuffles.put(seqNum, peers.toArray(new Host[0]));
            sendMessage(new ShuffleMessage(myself, peers, PRWL, seqNum), h);
            logger.debug("Sent ShuffleMessage to {}", h);
            seqNum = (short) ((short) (seqNum % Short.MAX_VALUE) + 1);
        }
    }

    private void uponHelloTimeout(HelloTimer timer, long timerId) {
        if(!active.fullWithPending(pending)){
            Host h = passive.dropRandom();
            if(h != null && pending.add(h)) {
                openConnection(h);
                logger.trace("Sending HelloMessage to {}, pending {}, active {}, passive {}", h, pending, active, passive);
                sendMessage(new HelloMessage(getPriority()), h);
                logger.debug("Sent HelloMessage to {}", h);
                timeout = (short) (Math.min(timeout * 2, MAX_BACKOFF));
            } else if(h != null)
                passive.addPeer(h);
        }
    }

    private void uponJoinTimeout(JoinTimer timer, long timerId) {
        if(active.isEmpty()) {
            Host contact = timer.getContact();
            openConnection(contact);
            logger.warn("Retrying join to {}", contact);
            JoinMessage m = new JoinMessage();
            sendMessage(m, contact);
            logger.debug("Sent JoinMessage to {}", contact);
            timer.incAttempts();
            setupTimer(timer, ((long) joinTimeout * timer.getAttempts()));
        }
    }


    /*--------------------------------- Procedures ---------------------------------------- */

    private boolean getPriority() {
        return active.getPeers().size() + pending.size() == 1;
    }

    private void addHostToActiveAndReplyToJoin(Host from) {
        openConnection(from);
        Host h = active.addPeer(from);
        logger.trace("Added to {} active{}", from, active);
        sendMessage( new JoinReplyMessage(), from);
        logger.debug("Sent JoinReplyMessage to {}", from);
        triggerNotification(new NeighbourUpNotification(from));
        handleDropFromActive(h);
    }


    /* --------------------------------- Channel Events ---------------------------- */

    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        logger.debug("Host {} is down, active{}, cause: {}", event.getNode(), active, event.getCause());
        if(active.removePeer(event.getNode())) {
            triggerNotification(new NeighbourDownNotification(event.getNode()));
            if(!active.fullWithPending(pending)){
                setupTimer(new HelloTimer(), timeout);
            }
        } else
            pending.remove(event.getNode());
    }

    private void uponOutConnectionFailed(OutConnectionFailed event, int channelId) {
        logger.trace("Connection to host {} failed, cause: {}", event.getNode(), event.getCause());
        if(active.removePeer(event.getNode())) {
            triggerNotification(new NeighbourDownNotification(event.getNode()));
            if(!active.fullWithPending(pending)){
                setupTimer(new HelloTimer(), timeout);
            }
        } else
            pending.remove(event.getNode());
    }

    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        logger.trace("Host (out) {} is up", event.getNode());
    }

    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        logger.trace("Host (in) {} is up", event.getNode());
    }

    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        logger.trace("Connection from host {} is down, active {}, cause: {}", event.getNode(), active, event.getCause());
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        // If there is a contact node, attempt to establish connection
        Host contactNode = computeContactNode(props);
        if (!myself.equals(contactNode)) {
            try {
                openConnection(contactNode);
                JoinMessage m = new JoinMessage();
                sendMessage(m, contactNode);
                logger.debug("Sent JoinMessage to {}", contactNode);
                logger.trace("Sent " + m + " to " + contactNode);
                setupTimer(new JoinTimer(contactNode), joinTimeout);
            } catch (Exception e) {
                logger.error("Invalid contact on configuration: '" + props.getProperty("contact"));
                e.printStackTrace();
                System.exit(-1);
            }
        } else {
            logger.debug("No contact node provided");
        }

        setupPeriodicTimer(new ShuffleTimer(), this.shuffleTime, this.shuffleTime);
    }

    private Host computeContactNode(Properties props) throws UnknownHostException {
        String contact = props.getProperty("contact");
        String[] hostElems = contact.split(":");
        return new Host(InetAddress.getByName(hostElems[0]), Short.parseShort(hostElems[1]));
    }

    @Override
    public Set<Host> getPeers() {
        return Set.copyOf(active.getPeers());
    }

    @Override
    public int getChannelID() {
        return channelId;
    }
}
