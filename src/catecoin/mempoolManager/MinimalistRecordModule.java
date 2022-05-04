package catecoin.mempoolManager;

import catecoin.blocks.chunks.MempoolChunk;
import catecoin.blocks.chunks.MinimalistMempoolChunk;
import catecoin.blocks.chunks.MinimalistSerializableChunk;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static java.nio.file.StandardOpenOption.APPEND;

public class MinimalistRecordModule {

    private static final Logger logger = LogManager.getLogger(MinimalistRecordModule.class);

    private final Optional<Path> recordFile;

    public MinimalistRecordModule(Properties props) throws IOException {
        boolean isRecordingBlocks = props.getProperty("isRecordingBlocks", "F").equals("T");
        recordFile = isRecordingBlocks ? Optional.of(MinimalistRecordModule.generateRecordFile(props)) : Optional.empty();
    }

    public static Path generateRecordFile(Properties props) throws IOException {
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

    public void recordBlocks(List<MempoolChunk> finalized) throws UnexpectedChunkTypeException, IOException {
        if (recordFile.isPresent()) {
            logger.info("Recording finalized blocks: {}", finalized);
            Gson gson = new Gson();
            for (MempoolChunk f : finalized) {
                if (!(f instanceof MinimalistMempoolChunk))
                    throw new UnexpectedChunkTypeException(MinimalistMempoolChunk.class.getSimpleName());
                MinimalistMempoolChunk min = (MinimalistMempoolChunk) f;
                MinimalistSerializableChunk content = new MinimalistSerializableChunk(min);
                String contentJson = gson.toJson(content);
                Files.writeString(recordFile.get(), contentJson + ", ", APPEND);
            }
        }
    }

}
