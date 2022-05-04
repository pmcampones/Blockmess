package sybilResistantCommitteeElection.poet.drand;

import catecoin.blockConstructors.BlockDirector;
import catecoin.txs.IndexableContent;
import ledger.Ledger;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import ledger.notifications.DeliverNonFinalizedBlockNotification;
import main.CryptographicUtils;
import main.ProtoPojo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.DRandRound;
import sybilResistantCommitteeElection.DRandUtils;
import sybilResistantCommitteeElection.notifications.IWasElectedWithBlockNotification;
import sybilResistantCommitteeElection.poet.timers.InitializationPeriodTimer;
import utils.IDGenerator;

import java.io.*;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.*;

import static java.lang.Integer.parseInt;

public class PoETWithDRand<C extends BlockContent<? extends IndexableContent>> extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(PoETWithDRand.class);

    public static final short ID = IDGenerator.genId();

    public static final int DIFFICULTY = 30 * 1000; //Milliseconds

    public static final int INITIALIZATION_TIME = 120 * 1000;

    private final DRandUtils dRandUtils;

    private final KeyPair self;

    private final int initializationTime;

    private final Thread waiter;

    private final Ledger<LedgerBlock<C,PoETDRandProof>> ledger;

    private final BlockDirector<?,C, LedgerBlock<C, PoETDRandProof>, PoETDRandProof> blockDirector;

    private Optional<LedgerBlock<C,PoETDRandProof>> currentBlock;

    public PoETWithDRand(Properties props, KeyPair self, Ledger<LedgerBlock<C,PoETDRandProof>> ledger,
                         BlockDirector<?, C, LedgerBlock<C, PoETDRandProof>, PoETDRandProof> blockDirector)
            throws HandlerRegistrationException {
        super(PoETWithDRand.class.getSimpleName(), ID);
        this.dRandUtils = new DRandUtils(props);
        this.self = self;
        this.ledger = ledger;
        this.blockDirector = blockDirector;
        this.waiter = new Thread(this::electionProcess);
        this.initializationTime = parseInt(props.getProperty("initializationTime",
                String.valueOf(INITIALIZATION_TIME)));
        this.currentBlock = Optional.empty();

        ProtoPojo.pojoSerializers.put(PoETDRandProof.ID, PoETDRandProof.serializer);

        registerTimerHandler(InitializationPeriodTimer.ID, (InitializationPeriodTimer timer, long id) -> uponInitializationPeriodTimer());
        subscribeNotification(DeliverNonFinalizedBlockNotification.ID,
                (DeliverNonFinalizedBlockNotification<LedgerBlock<C, PoETDRandProof>> notif, short source) -> uponDeliverNonFinalizedBlockNotification(notif));
    }

    @Override
    public void init(Properties properties) {
        logger.debug("Starting to generate Sybil Resistant Proof for block in {} miliseconds.",
                initializationTime);
        setupTimer(new InitializationPeriodTimer(), initializationTime);
    }

    private void uponInitializationPeriodTimer() {
        waiter.start();
    }

    private void electionProcess(){
        while(true) {
            try {
                Set<UUID> previous = ledger.getBlockR();
                PoETDRandProof proof = compileDRandProof();
                LedgerBlock<C, PoETDRandProof> block = blockDirector.createBlockProposal(previous, proof);
                currentBlock = Optional.of(block);
                logger.info("Generated DRandProof for block {}. Going to sleep for {}",
                        block.getBlockId(), proof.getWaitTime());
                Thread.sleep(proof.getWaitTime());
                currentBlock = Optional.empty();
                triggerNotification(new IWasElectedWithBlockNotification<>(block));
            } catch (InterruptedException e) {
                logger.info("Interrupted sleep because another node completed a block for this Sybil Resistant round");
            } catch (Exception e) {
                logger.info("Unable to compute DRandProof because exception: '{}'", e.getMessage());
            }
        }
    }

    public PoETDRandProof compileDRandProof() throws Exception {
        DRandRound latest = dRandUtils.getLatestDRandRound();
        //DRandRound latest = new DRandRound(10, "random", "sig", "sig2");    //Use when there is no internet.
        assert latest != null;
        int salt = new Random().nextInt();
        byte[] randomness = latest.getRandomnessStr().getBytes();
        byte[] seed = computeSeedUsed(self.getPublic(), randomness, salt);
        int timeWaiting = getWaitTimeFromSeed(seed);
        return new PoETDRandProof(latest.getRound(), randomness, salt, timeWaiting);
    }

    public static byte[] computeSeedUsed(PublicKey proposer, byte[] randomness, int salt) throws IOException {
        try(var out = new ByteArrayOutputStream();
            var oout = new ObjectOutputStream(out)) {
            oout.writeObject(proposer);
            oout.write(randomness);
            oout.writeInt(salt);
            oout.flush();
            return out.toByteArray();
        }
    }

    public static int getWaitTimeFromSeed(byte[] seed) throws IOException, NoSuchAlgorithmException {
        return Math.abs(getIntFromSeed(seed)) % DIFFICULTY;
    }

    private static int getIntFromSeed(byte[] seed) throws IOException, NoSuchAlgorithmException {
        byte[] hashedContent = MessageDigest.getInstance(CryptographicUtils.HASH_ALGORITHM).digest(seed);
        try (var in = new DataInputStream(new ByteArrayInputStream(hashedContent))) {
            return in.readInt();
        }
    }

    private void uponDeliverNonFinalizedBlockNotification(
            DeliverNonFinalizedBlockNotification<LedgerBlock<C, PoETDRandProof>> notif) {
        logger.debug("Received non finalized block {}", notif.getNonFinalizedBlock().getBlockId());
        currentBlock.ifPresent(curr -> {
            LedgerBlock<C, PoETDRandProof> delivered = notif.getNonFinalizedBlock();
            Set<UUID> deliveredPrev = Set.copyOf(delivered.getPrevRefs());
            Set<UUID> currPrev = Set.copyOf(curr.getPrevRefs());
            if (deliveredPrev.equals(currPrev))
                waiter.interrupt();
        });
    }
}
