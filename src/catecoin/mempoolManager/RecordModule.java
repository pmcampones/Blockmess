package catecoin.mempoolManager;

import catecoin.blocks.chunks.MempoolChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static java.nio.file.StandardOpenOption.APPEND;

public interface RecordModule {

    void recordBlocks (List<MempoolChunk> finalized) throws UnexpectedChunkTypeException, IOException;

    static Path generateRecordFile(Properties props) throws IOException {
        String recordString = props.getProperty("recordFile", "bootstrapContent/bootstrap.txt");
        Path recordFile = Path.of(recordString);
        boolean isBootstraped = props.getProperty("isBootstraped", "F").equals("T");
        if (!isBootstraped)
            Files.deleteIfExists(recordFile);
        Files.createDirectories(recordFile.getParent());
        Files.createFile(recordFile);
        Files.writeString(recordFile, "[", APPEND);
        return recordFile;
    }

}
