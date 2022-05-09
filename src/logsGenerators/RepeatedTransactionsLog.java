package logsGenerators;

import catecoin.blocks.ContentList;
import catecoin.notifications.DeliverFinalizedBlockIdentifiersNotification;
import com.google.common.collect.Sets;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.AppContent;
import ledger.notifications.DeliverNonFinalizedBlockNotification;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import utils.IDGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.util.stream.Collectors.toList;

public class RepeatedTransactionsLog extends GenericProtocol {

    public static final short ID = IDGenerator.genId();

    private static final String TX_REPETITION_OUTPUT_FILE = "outputLogs/txRepetition";

    private final Path txRepetitionOutputFile;

    private final Set<UUID> finalizedTxs = Sets.newConcurrentHashSet();

    private final Map<UUID, BlockmessBlock> nonFinalizedBlocks = new ConcurrentHashMap<>();

    public RepeatedTransactionsLog(Properties props) throws HandlerRegistrationException, IOException {
        super(RepeatedTransactionsLog.class.getSimpleName(), ID);
        this.txRepetitionOutputFile = Path.of(props.getProperty("txRepetitionOutputFile",
                TX_REPETITION_OUTPUT_FILE));
        Files.deleteIfExists(txRepetitionOutputFile);
        Files.createDirectories(txRepetitionOutputFile.getParent());
        Files.createFile(txRepetitionOutputFile);
        Files.writeString(txRepetitionOutputFile, "", APPEND);
        subscribeNotification(DeliverNonFinalizedBlockNotification.ID,
                (DeliverNonFinalizedBlockNotification<BlockmessBlock> notif1, short source1) -> uponDeliverNonFinalizedBlockNotification(notif1));
        subscribeNotification(DeliverFinalizedBlockIdentifiersNotification.ID,
                (DeliverFinalizedBlockIdentifiersNotification notif, short source) -> uponDeliverFinalizedBlockIdentifiers(notif));
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {}

    private void uponDeliverNonFinalizedBlockNotification(
            DeliverNonFinalizedBlockNotification<BlockmessBlock> notif) {
        BlockmessBlock block = notif.getNonFinalizedBlock();
        nonFinalizedBlocks.put(block.getBlockId(), block);
    }

    private void uponDeliverFinalizedBlockIdentifiers(
            DeliverFinalizedBlockIdentifiersNotification notif) {
        notif.getDiscardedBlockIds().forEach(nonFinalizedBlocks::remove);
        List<UUID> txs = notif.getFinalizedBlocksIds().stream()
                .map(nonFinalizedBlocks::get)
                //.filter(Objects::nonNull)   //Shouldn't happen
                .map(BlockmessBlock::getContentList)
                .map(ContentList::getContentList)
                .flatMap(Collection::stream)
                .map(AppContent::getId)
                .collect(toList());
        notif.getFinalizedBlocksIds().forEach(nonFinalizedBlocks::remove);
        for (UUID tx : txs)
            if (finalizedTxs.contains(tx)) {
                try {
                    Files.writeString(txRepetitionOutputFile, tx.toString() + "\n", APPEND);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else
                finalizedTxs.add(tx);
    }

}
