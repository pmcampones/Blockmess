package sybilResistantCommitteeElection.poet.gpoet;

import catecoin.blockConstructors.BlockDirector;
import catecoin.txs.IndexableContent;
import catecoin.validators.GPoETValidator;
import ledger.Ledger;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import ledger.blocks.LedgerBlockImp;
import ledger.notifications.DeliverNonFinalizedBlockNotification;
import main.CryptographicUtils;
import main.ProtoPojo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.notifications.IWasElectedWithBlockNotification;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;
import sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers.LedgerGPoETDifficultyComputer;
import sybilResistantCommitteeElection.poet.timers.InitializationPeriodTimer;
import utils.IDGenerator;
import utils.merkleTree.ConcurrentMerkleTree;
import utils.merkleTree.MerkleRoot;
import utils.merkleTree.MerkleTree;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Integer.parseInt;
import static sybilResistantCommitteeElection.poet.gpoet.gpoetDifficultyComputers.LedgerGPoETDifficultyComputer.TIME_BETWEEN_QUERIES;

public class GPoET<E extends IndexableContent, C extends BlockContent<E>> extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(GPoET.class);

    public static final short ID = IDGenerator.genId();

    public static final int INITIALIZATION_TIME = 120 * 1000;

    private UUID prevRef;

    private C currBlockContent;

    private final int timeBetweenQueries;

    private final Ledger<LedgerBlock<C,GPoETProof>> ledger;

    private final BlockDirector<?,C,LedgerBlock<C,GPoETProof>,GPoETProof> blockDirector;

    private final Thread electionThread;

    private final int initializationTime;

    private final KeyPair self;

    private final MerkleTree randomSeed;

    private final ReentrantLock randomSeedLock = new ReentrantLock();

    private final LedgerGPoETDifficultyComputer difficultyComputer;

    public GPoET(Properties props, Ledger<LedgerBlock<C, GPoETProof>> ledger,
                 BlockDirector<?, C, LedgerBlock<C, GPoETProof>, GPoETProof> blockDirector, KeyPair self) throws HandlerRegistrationException, IOException {
        super(GPoET.class.getSimpleName(), IDGenerator.genId());
        this.initializationTime = parseInt(props.getProperty("initializationTime",
                String.valueOf(INITIALIZATION_TIME)));
        this.self = self;
        this.timeBetweenQueries = parseInt(props.getProperty("timeBetweenQueries", TIME_BETWEEN_QUERIES));
        this.difficultyComputer = new LedgerGPoETDifficultyComputer(props);
        this.ledger = ledger;
        this.blockDirector = blockDirector;
        this.electionThread = new Thread(this::electionProcess);
        this.randomSeed = new ConcurrentMerkleTree(computeRandomSeed());

        ProtoPojo.pojoSerializers.put(GPoETProof.ID, GPoETProof.serializer);
        registerTimerHandler(InitializationPeriodTimer.ID, (InitializationPeriodTimer timer, long id) -> uponInitializationPeriodTimer());
        subscribeNotification(DeliverNonFinalizedBlockNotification.ID,
                (DeliverNonFinalizedBlockNotification<LedgerBlock<C, PoETDRandProof>> notif, short source) -> uponDeliverNonFinalizedBlockNotification(notif));
    }

    private MerkleTree computeRandomSeed() throws IOException {
        byte[] pk = self.getPublic().getEncoded();
        prevRef = ledger.getBlockR().iterator().next();
        byte[] prevBytes = getIdBytes(prevRef);
        currBlockContent = blockDirector.createBlockContent(Set.of(prevRef), GPoETProof.SERIALIZED_SIZE);
        return new MerkleRoot(List.of(pk, prevBytes, currBlockContent.getContentHash()));
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {
        logger.debug("Starting to generate Sybil Resistant Proof for block in {} miliseconds.",
                initializationTime);
        setupTimer(new InitializationPeriodTimer(), initializationTime);
    }

    private void uponInitializationPeriodTimer() {
        electionThread.start();
    }

    private void electionProcess() {
        while(true) {
            try {
                GPoETProof proof = computeProof();
                attemptToProposeBlock(proof);
            } catch (InterruptedException ignored) {
                //Someone else found a proof
            } catch (IOException | NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
                e.printStackTrace();
            }
        }
    }

    private void attemptToProposeBlock(GPoETProof proof) throws IOException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        if (GPoETValidator.isProofValid(proof, List.of(prevRef), currBlockContent, self.getPublic(), difficultyComputer)) {
            try {
                randomSeedLock.lock();
                if (GPoETValidator.isProofValid(proof, List.of(prevRef), currBlockContent, self.getPublic(), difficultyComputer)) {
                    LedgerBlock<C, GPoETProof> block = new LedgerBlockImp<>(1,
                            List.of(prevRef), currBlockContent, proof, self);
                    triggerNotification(new IWasElectedWithBlockNotification<>(block));
                }
            } finally {
                randomSeedLock.unlock();
            }
        }
    }

    private GPoETProof computeProof() throws InterruptedException {
        byte[] solution;
        int nonce = 0;
        do {
            nonce = (nonce + 1) % Integer.MAX_VALUE;
            solution = computeSolution(randomSeed, nonce);
            Thread.sleep(timeBetweenQueries);
        } while (!difficultyComputer.hasEnoughLeadingZeros(solution));
        return new GPoETProof(nonce);
    }

    public static byte[] computeSolution(MerkleTree randomSeed, int nonce) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(randomSeed.getHashValue());
        byteBuffer.putInt(nonce);
        return CryptographicUtils.hashInput(byteBuffer.array());
    }

    private void uponDeliverNonFinalizedBlockNotification(
            DeliverNonFinalizedBlockNotification<LedgerBlock<C, PoETDRandProof>> notif) {
        logger.debug("Received non finalized block {}", notif.getNonFinalizedBlock().getBlockId());
        try {
            receiveUpdate();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void receiveUpdate() throws IOException {
        Set<UUID> prevs = ledger.getBlockR();
        if (!prevs.isEmpty() && !prevs.contains(prevRef)) {
            updateRandomSeed(prevs);
            if (electionThread.getState() == Thread.State.WAITING)
                electionThread.interrupt();
        }
    }

    private void updateRandomSeed(Set<UUID> prevs) throws IOException {
        try {
            randomSeedLock.lock();
            replacePrevRef(prevs);
            replaceBlockContent();
        } finally {
            randomSeedLock.unlock();
        }
    }

    private void replacePrevRef(Set<UUID> prevs) {
        byte[] oldPrevBytes = getIdBytes(prevRef);
        prevRef = prevs.iterator().next();
        byte[] newPrevBytes = getIdBytes(prevRef);
        randomSeed.replaceLeaf(oldPrevBytes, newPrevBytes);
    }

    private byte[] getIdBytes(UUID id) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(2 * Long.BYTES);
        byteBuffer.putLong(id.getMostSignificantBits());
        byteBuffer.putLong(id.getLeastSignificantBits());
        return byteBuffer.array();
    }

    private void replaceBlockContent() throws IOException {
        byte[] oldBlockContent = currBlockContent.getContentHash();
        currBlockContent = blockDirector.createBlockContent(Set.of(prevRef), 256 + Integer.BYTES);
        byte[] newBlockContent = currBlockContent.getContentHash();
        randomSeed.replaceLeaf(oldBlockContent, newBlockContent);
    }

}
