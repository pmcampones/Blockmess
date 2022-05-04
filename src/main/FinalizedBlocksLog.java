package main;

import catecoin.blocks.SimpleBlockContentList;
import ledger.LedgerObserver;
import ledger.blocks.LedgerBlock;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.IDGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;

public class FinalizedBlocksLog implements LedgerObserver {

    private static final Logger logger = LogManager.getLogger(FinalizedBlocksLog.class);

    public static final short ID = IDGenerator.genId();

    private static final String FINALIZED_BLOCKS_OUTPUT_FILE = "outputLogs/finalizedBlocks";

    private final Path finalizedBlocksOutputFile;

    private final Map<UUID, Pair<Long, Integer>> blockArrival = new ConcurrentHashMap<>();

    public FinalizedBlocksLog(Properties props) throws IOException {
        this.finalizedBlocksOutputFile = Path.of(props.getProperty("finalizedBlocksOutputFile",
                FINALIZED_BLOCKS_OUTPUT_FILE));
        Files.deleteIfExists(finalizedBlocksOutputFile);
        Files.createDirectories(finalizedBlocksOutputFile.getParent());
        Files.createFile(finalizedBlocksOutputFile);
        Files.writeString(finalizedBlocksOutputFile, "BlockID - Delivery Time - Finalization Time\n", APPEND);
    }

    @Override
    public void deliverNonFinalizedBlock(LedgerBlock block, int weight) {
        long receiveTime = System.currentTimeMillis();
        SimpleBlockContentList content =
                (SimpleBlockContentList) block.getBlockContent();
        logger.debug("Recording arrival of block {} at time {}.",
                block.getBlockId(), receiveTime);
        blockArrival.put(block.getBlockId(),
                Pair.of(System.currentTimeMillis(), content.getContentList().size()));
    }

    @Override
    public void deliverFinalizedBlocks(List finalized, Set discarded) {
        List<UUID> finalizedIds = (List<UUID>) finalized.stream()
                .map(obj -> ((UUID) obj))
                .collect(Collectors.toList());
        List<UUID> discardedIds = (List<UUID>) discarded.stream()
                .map(obj -> ((UUID) obj))
                .collect(Collectors.toList());
        long deliveryTime = System.currentTimeMillis();
        logger.debug("Recording blocks {} at time {}",
                finalized, deliveryTime);
        for (UUID finBlock : finalizedIds) {
            Pair<Long, Integer> tuple = blockArrival.remove(finBlock);
            if (tuple != null) {
                long elapsed = deliveryTime - tuple.getLeft();
                String output = String.format("%s - %d - %d - %d\n",
                        finBlock, deliveryTime, elapsed, tuple.getRight());
                try {
                    Files.writeString(finalizedBlocksOutputFile, output, APPEND);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        discardedIds.forEach(blockArrival::remove);
    }
}
