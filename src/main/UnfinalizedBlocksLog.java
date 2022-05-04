package main;

import ledger.LedgerObserver;
import ledger.blocks.LedgerBlock;
import utils.IDGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.file.StandardOpenOption.APPEND;

public class UnfinalizedBlocksLog implements LedgerObserver {

    private static final String UNFINALIZED_BLOCKS_OUTPUT_FILE = "outputLogs/unfinalizedBlocks";

    private final Path unfinalizedBlocksOutputFile;

    private final Set<UUID> repeats = new HashSet<>();

    public UnfinalizedBlocksLog(Properties props) throws IOException {
        this.unfinalizedBlocksOutputFile = Path.of(props.getProperty("unfinalizedBlocksOutputFile",
                UNFINALIZED_BLOCKS_OUTPUT_FILE));
        Files.deleteIfExists(unfinalizedBlocksOutputFile);
        Files.createDirectories(unfinalizedBlocksOutputFile.getParent());
        Files.createFile(unfinalizedBlocksOutputFile);
        Files.writeString(unfinalizedBlocksOutputFile, "BlockID - Delivery Time\n");
    }

    @Override
    public void deliverNonFinalizedBlock(LedgerBlock block, int weight) {
        UUID blockId = block.getBlockId();
        if (repeats.contains(blockId))
            return;
        long time = System.currentTimeMillis();
        repeats.add(blockId);
        String output = String.format("%s - %d - %d\n",blockId, time,
                block.getBlockContent().getContentList().size());
        try {
            Files.writeString(unfinalizedBlocksOutputFile, output, APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deliverFinalizedBlocks(List finalized, Set discarded) {}
}
