package sybilResistantCommitteeElection.pos.sortition;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import catecoin.blockConstructors.BlockDirector;
import catecoin.notifications.AnswerMessageValidationNotification;
import catecoin.posSpecific.accountManagers.AccountManager;
import catecoin.posSpecific.keyBlockManagers.KeyBlockManager;
import catecoin.txs.IndexableContent;
import catecoin.validators.SortitionProofValidator;
import ledger.Ledger;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import main.CryptographicUtils;
import main.ProtoPojo;
import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.DRandRound;
import sybilResistantCommitteeElection.DRandUtils;
import sybilResistantCommitteeElection.notifications.IWasElectedWithBlockNotification;
import sybilResistantCommitteeElection.pos.sortition.proofs.*;
import sybilResistantCommitteeElection.pos.sortition.timers.BetweenBlockProposalsTimer;
import sybilResistantCommitteeElection.pos.sortition.timers.ExchangeProofPeriodTimer;
import sybilResistantCommitteeElection.pos.sortition.timers.WaitForInitialRoundTimer;
import sybilResistantCommitteeElection.pos.sortition.timers.WaitForNextElectionRoundTimer;
import utils.IDGenerator;
import valueDispatcher.ValueDispatcher;
import valueDispatcher.notifications.DeliverSortitionProofNotification;
import valueDispatcher.requests.DisseminateSortitionProofRequest;

import java.io.IOException;
import java.security.*;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;

public class PoSAlgorandSortitionWithDRand<C extends BlockContent<? extends IndexableContent>> extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(PoSAlgorandSortitionWithDRand.class);

    public static final short ID = IDGenerator.genId();

    public static final int EXPECTED_PROPOSERS = 10;

    private static final int ADVANCE_ROUND_MULTIPLES = 10;

    private static final int INITIAL_DRAND_ROUND = 1;

    private static final long OFFSET_WAIT_PERIOD = 5000;

    private static final long UNDEFINED_ID = -1;

    /**
     * The DRand public randomness beacon refreshes the
     * current randomness round every 30 seconds.
     * <p>This parameter is not parameterizable, as it is defined
     * by the creators of the beacon.</p>
     */
    private static final long DRAND_ROUND_PERIOD = 30000;

    private static final long ERROR_WAIT_PERIOD = 300000;

    private final DRandUtils dRandUtils;

    private final KeyPair myKeys;

    private final AccountManager accountManager;

    private final KeyBlockManager keyBlockManager;

    private final SortitionProofValidator sortitionProofValidator;

    private final Ledger<LedgerBlock<C, SortitionProof>> ledger;

    private final BlockDirector<?,C,LedgerBlock<C,SortitionProof>, SortitionProof> blockDirector;

    private final int expectedProposers;

    private final int initialDrandRound;

    private final int advanceRoundMultiples;

    /**
     * Wait between verifications on whether we have attained the correct round.
     */
    private final long waitForInitialRoundWaitPeriod;

    private final long errorWaitPeriod;

    private final long exchangeProofPeriodWait;

    private final long betweenBlockProposalsWait;

    private final ReadWriteLock currentRoundLock = new ReentrantReadWriteLock();

    private Optional<InElectionSortitionProof> currentRound = Optional.empty();

    private Optional<UUID> myLatestKeyBlockProposal = Optional.empty();

    private boolean amProposer = false;

    private long exchangeProofTimerId, betweenBlocksProposalId;

    public PoSAlgorandSortitionWithDRand(Properties props, KeyPair myKeys, AccountManager accountManager,
                                         KeyBlockManager keyBlockManager, Ledger<LedgerBlock<C, SortitionProof>> ledger,
                                         BlockDirector<?,C,LedgerBlock<C,SortitionProof>, SortitionProof> blockDirector)
            throws HandlerRegistrationException {
        super(PoSAlgorandSortitionWithDRand.class.getSimpleName(), ID);
        this.dRandUtils = new DRandUtils(props);
        this.myKeys = myKeys;
        this.accountManager = accountManager;
        this.keyBlockManager = keyBlockManager;
        this.ledger = ledger;
        this.blockDirector = blockDirector;
        this.sortitionProofValidator = new SortitionProofValidator(props, accountManager);
        this.expectedProposers = parseInt(props.getProperty("expectedProposers",
                String.valueOf(EXPECTED_PROPOSERS)));
        this.initialDrandRound = parseInt(props.getProperty("initialDrandRound",
                String.valueOf(INITIAL_DRAND_ROUND)));
        this.advanceRoundMultiples = parseInt(props.getProperty("advanceRoundMultiples",
                String.valueOf(ADVANCE_ROUND_MULTIPLES)));
        this.waitForInitialRoundWaitPeriod = parseLong(props.getProperty("offsetWaitPeriod",
                String.valueOf(OFFSET_WAIT_PERIOD)));
        this.errorWaitPeriod = parseLong(props.getProperty("errorWaitPeriod",
                String.valueOf(ERROR_WAIT_PERIOD)));
        this.exchangeProofPeriodWait = parseLong(props.getProperty("exchangeProofPeriodWait",
                String.valueOf(ExchangeProofPeriodTimer.WAIT_PERIOD)));
        this.betweenBlockProposalsWait = parseLong(props.getProperty("betweenBlockProposalsWait",
                String.valueOf(BetweenBlockProposalsTimer.WAIT_PERIOD)));

        this.exchangeProofTimerId = this.betweenBlocksProposalId = UNDEFINED_ID;

        registerSerializers();
        registerTimerHandlers();
        subscribeNotifications();
    }

    private void registerSerializers() {
        ProtoPojo.pojoSerializers.put(InElectionSortitionProof.ID, InElectionSortitionProof.serializer);
        ProtoPojo.pojoSerializers.put(MicroBlockSortitionProof.ID, MicroBlockSortitionProof.serializer);
        ProtoPojo.pojoSerializers.put(KeyBlockSortitionProof.ID, KeyBlockSortitionProof.serializer);
    }

    private void registerTimerHandlers() throws HandlerRegistrationException {
        registerTimerHandler(WaitForInitialRoundTimer.ID,
                (WaitForInitialRoundTimer timer3, long timerId3) -> uponWaitInitialRoundTimer());
        registerTimerHandler(WaitForNextElectionRoundTimer.ID,
                (WaitForNextElectionRoundTimer timer2, long timerId2) -> uponWaitForNextElectionRoundTimer());
        registerTimerHandler(ExchangeProofPeriodTimer.ID,
                (ExchangeProofPeriodTimer timer1, long timerId1) -> uponExchangeProofPeriodTimer());
        registerTimerHandler(BetweenBlockProposalsTimer.ID,
                (BetweenBlockProposalsTimer timer, long timerId) -> uponBetweenBlocksProposalTimer());
    }

    private void subscribeNotifications() throws HandlerRegistrationException {
        subscribeNotification(DeliverSortitionProofNotification.ID,
                (DeliverSortitionProofNotification notif, short source) -> uponDeliverSortitionProofNotification(notif));
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {
        reachInitialRound();
    }

    private void uponWaitInitialRoundTimer() {
        reachInitialRound();
    }

    private void reachInitialRound() {
        try {
            tryToReachInitialRound();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void tryToReachInitialRound() throws Exception {
        DRandRound latestSeenRound = dRandUtils.getLatestDRandRound();
        if (latestSeenRound.getRound() < initialDrandRound) {
            long sleepTime = (long) (initialDrandRound - latestSeenRound.getRound())
                    * DRAND_ROUND_PERIOD + waitForInitialRoundWaitPeriod;
            logger.info("Started in DRand round: {}, " +
                    "but established initial round is {}.\n" +
                    "Waiting {} miliseconds.", currentRound, initialDrandRound, sleepTime);
            setupTimer(new WaitForInitialRoundTimer(), sleepTime);
        } else {
            waitUntilNextRound();
        }
    }

    private void uponWaitForNextElectionRoundTimer() {
        waitUntilNextRound();
    }

    private void waitUntilNextRound() {
        try {
            tryToWaitUntilNextRound();
        } catch (Exception e) {
            logger.error("Unable to participate in next round because: {}\n" +
                    "Waiting until {} ms until possible network error is solved.",
                    e.getMessage(), errorWaitPeriod);
            e.printStackTrace();
            setupTimer(new WaitForNextElectionRoundTimer(), errorWaitPeriod);
        }
    }

    private void tryToWaitUntilNextRound() throws Exception {
        DRandRound latestSeenRound = dRandUtils.getLatestDRandRound();
        int rem = latestSeenRound.getRound() % advanceRoundMultiples;
        if (rem == 0) {
            logger.info("Reached next election round: {}", latestSeenRound.getRound());
            participateInElectionRound(latestSeenRound);
        }
        logger.debug("Currently on round {}", latestSeenRound.getRound());
        long sleepTime = (long) (advanceRoundMultiples - rem - 1) *
                DRAND_ROUND_PERIOD + waitForInitialRoundWaitPeriod;
        logger.debug("Sleeping for {} miliseconds.", sleepTime);
        setupTimer(new WaitForNextElectionRoundTimer(), sleepTime);
    }

    private void participateInElectionRound(DRandRound latestSeenRound) throws Exception {
        byte[] randomSeed = latestSeenRound.getRandomnessStr().getBytes();
        IncompleteSortitionProof proof = countVotes(randomSeed, latestSeenRound.getRound());
        logger.info("I have {} votes from my {} coins in the current election round.",
                proof.getVotes(), accountManager.getProposerCoins(myKeys.getPublic(),
                        keyBlockManager.getHeaviestKeyBlock()));
        InElectionSortitionProof electionProof = new InElectionSortitionProof(proof, myKeys.getPublic());
        if (proof.getVotes() > 0 && isPriorityProofReadLock(proof)) {
            if (replaceProofWithMineOwn(proof)) {
                logger.debug("My proof of {} votes is the best proof I've seen for round {}",
                        proof.getVotes(), proof.getRound());
                sendRequest(new DisseminateSortitionProofRequest(electionProof), ValueDispatcher.ID);
            }
        }
    }

    private IncompleteSortitionProof countVotes(byte[] randomSeed, int round) throws SignatureException, InvalidKeyException {
        byte[] hashProof = CryptographicUtils.getFieldsSignature(randomSeed, myKeys.getPrivate());
        UUID prevKeyBlock = keyBlockManager.getHeaviestKeyBlock();
        int votes = countVotesFromProof(hashProof, myKeys.getPublic(), prevKeyBlock);
        return new IncompleteSortitionProof(round, votes,
                prevKeyBlock, hashProof);
    }

    private int countVotesFromProof(byte[] hashProof, PublicKey proposer, UUID prevKeyBlock) {
        long hashVal = SortitionProofValidator.computeHashVal(hashProof);
        long maxInt = (long) (Math.pow(2, Integer.BYTES * 8) - 1);
        double probability = ((double) expectedProposers) / accountManager.getCirculationCoins();
        double hashRatio = ((double) hashVal) / maxInt;
        int proposerCoins = accountManager.getProposerCoins(proposer,
                prevKeyBlock);
        return computeNumberVotes(hashRatio, proposerCoins, probability);
    }

    public static int computeNumberVotes(double hashRatio, int proposerCoins, double probability) {
        BinomialDistribution b = new BinomialDistribution(proposerCoins, probability);
        double currProb = 0;
        int numVotes = -1;
        do {
            numVotes++;
            currProb += b.probability(numVotes);
        } while (hashRatio > currProb);
        return numVotes;
    }

    private boolean isPriorityProofReadLock(IncompleteSortitionProof proof) {
        try {
            currentRoundLock.readLock().lock();
            return isPriorityProof(proof);
        } finally {
            currentRoundLock.readLock().unlock();
        }
    }

    private boolean replaceProofWithMineOwn(IncompleteSortitionProof proof) {
        try {
            currentRoundLock.writeLock().lock();
            return tryToReplaceProofWithMineOwn(proof);
        } finally {
            currentRoundLock.writeLock().unlock();
        }
    }

    private boolean tryToReplaceProofWithMineOwn(IncompleteSortitionProof proof) {
        if (isPriorityProof(proof)) {
            InElectionSortitionProof electionProof = new InElectionSortitionProof(proof, myKeys.getPublic());
            currentRound = Optional.of(electionProof);
            amProposer = true;
            this.exchangeProofTimerId = setupTimer(new ExchangeProofPeriodTimer(), exchangeProofPeriodWait);
            return true;
        }
        return false;
    }

    private void uponDeliverSortitionProofNotification(DeliverSortitionProofNotification notif) {
        InElectionSortitionProof electionProof = notif.getProof();
        PublicKey proposer = electionProof.getProposer();
        IncompleteSortitionProof proof = electionProof.getProof();
        boolean isValid = false;
        if (isPriorityProofReadLock(proof) && sortitionProofValidator.isSortitionProofValid(proof, proposer)) {
            if (replaceProofWithOtherNode(electionProof)) {
                isValid = true;
                logger.info("Received a proof with {} votes with higher priority than any I've seen for round {}",
                        proof.getVotes(), proof.getRound());
                cancelTimer(this.exchangeProofTimerId);
                cancelTimer(this.betweenBlocksProposalId);
            }
        }
        notifyKeepBroadcasting(electionProof, isValid);
    }

    private void notifyKeepBroadcasting(InElectionSortitionProof proof, boolean isValid) {
        try {
            triggerNotification(new AnswerMessageValidationNotification(isValid, proof.getBlockingID()));
        } catch (InnerValueIsNotBlockingBroadcast e) {
           logger.error("Unable to notify broadcast of validity of proof because: {}",
                   e.getMessage());
        }
    }

    private boolean replaceProofWithOtherNode(InElectionSortitionProof proof) {
        try {
            currentRoundLock.writeLock().lock();
            return tryToReplaceProofWithOtherNode(proof);
        } finally {
            currentRoundLock.writeLock().unlock();
        }
    }

    private boolean tryToReplaceProofWithOtherNode(InElectionSortitionProof proof) {
        if (isPriorityProof(proof.getProof())) {
            currentRound = Optional.of(proof);
            amProposer = false;
            return true;
        }
        return false;
    }

    private boolean isPriorityProof(IncompleteSortitionProof newProof) {
        return currentRound.isEmpty() || newProof.hasPriorityOver(currentRound.get().getProof());
    }

    private void uponExchangeProofPeriodTimer() {
        try {
            tryToStartProposingBlocks();
        } catch (Exception e) {
            logger.error("Unable to start proposing blocks because: {}\n" +
                            "No blocks will be proposed in this Sybil Election Round.",
                    e.getMessage());
        }
    }

    private void tryToStartProposingBlocks() throws Exception {
        DRandRound latest = dRandUtils.getLatestDRandRound();
        currentRound.ifPresent(proof -> {
            if (latest.getRound() <= proof.getRound()) {
                logger.debug("Waiting {} ms to see if I can propose blocks", exchangeProofPeriodWait);
                this.exchangeProofTimerId = setupTimer(new ExchangeProofPeriodTimer(), exchangeProofPeriodWait);
            } else if (amProposer) {
                assert (latest.getRound() == proof.getRound() + 1);
                proposeKeyBlock(latest);
                cancelTimer(this.betweenBlocksProposalId);
                this.betweenBlocksProposalId = setupPeriodicTimer(new BetweenBlockProposalsTimer(),
                        betweenBlockProposalsWait, betweenBlockProposalsWait);
            }
        });
    }

    private void uponBetweenBlocksProposalTimer() {
        proposeMicroBlock();
    }

    private void proposeKeyBlock(DRandRound latest) {
        try {
            currentRoundLock.readLock().lock();
            tryToProposeKeyBlock(latest);
        } catch (IOException | SignatureException | NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Unable to propose a key block because: {}",
                    e.getMessage());
        } finally {
            currentRoundLock.readLock().unlock();
        }
    }

    private void tryToProposeKeyBlock(DRandRound latest) throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        if (currentRound.isPresent()) {
            InElectionSortitionProof proof = currentRound.get();
            byte[] randomSeed = latest.getRandomnessStr().getBytes();
            KeyBlockSortitionProof timelyProof = new KeyBlockSortitionProof(proof.getProof(), randomSeed);
            Set<UUID> previous = ledger.getBlockR();
            LedgerBlock<C, SortitionProof> block = blockDirector.createBlockProposal(previous, timelyProof);
            logger.info("Proposing Key block {}, following block {} and referencing prev key block {}",
                    block.getBlockId(), block.getPrevRefs(), proof.getKeyBlockId());
            triggerNotification(new IWasElectedWithBlockNotification<>(block));
            myLatestKeyBlockProposal = Optional.of(block.getBlockId());
        }
    }

    private void proposeMicroBlock() {
        try {
            currentRoundLock.readLock().lock();
            tryToProposeMicroBlock();
        } catch (IOException | SignatureException | NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Unable to propose micro block because: {}",
                    e.getMessage());
        } finally {
            currentRoundLock.readLock().unlock();
        }
    }

    private void tryToProposeMicroBlock() throws IOException, SignatureException,
            NoSuchAlgorithmException, InvalidKeyException {
        if (currentRound.isPresent()
                && currentRound.get().getProposer().equals(myKeys.getPublic())
                && myLatestKeyBlockProposal.isPresent()) {
            MicroBlockSortitionProof proof = new MicroBlockSortitionProof(myLatestKeyBlockProposal.get());
            Set<UUID> previous = ledger.getBlockR();
            LedgerBlock<C, SortitionProof> block = blockDirector.createBlockProposal(previous, proof);
            logger.debug("Proposing microblock {}", block.getBlockId());
            triggerNotification(new IWasElectedWithBlockNotification<>(block));
        }
    }

}
