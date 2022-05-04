package catecoin.mempoolManager;

import catecoin.blocks.chunks.MempoolChunk;
import catecoin.blocks.chunks.RichSerializableChunk;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static java.util.Collections.emptyList;

public class RichBootstrapModule implements BootstrapModule {

    private static final Logger logger = LogManager.getLogger(RichBootstrapModule.class);

    @Override
    public List<MempoolChunk> getStoredChunks(Properties props) {
        try {
            return tryToGetStoredChunks(props);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("Unable to bootstrap DL because: {}", e.getMessage());
        }
        return emptyList();
    }

    private List<MempoolChunk> tryToGetStoredChunks(Properties props) throws IOException,
            NoSuchAlgorithmException, InvalidKeySpecException {
        boolean isBootstraped = props.getProperty("isBootstraped", "F").equals("T");
        if (isBootstraped) {
            String bootstrapFile = props.getProperty("bootstrapFile",
                    "./bootstrapContent/bootstrap.txt");
            String bootstrapJson = Files.readString(Path.of(bootstrapFile));
            RichSerializableChunk[] serializableChunks = new Gson()
                    .fromJson(bootstrapJson, RichSerializableChunk[].class);
            List<MempoolChunk> chunks = new ArrayList<>(serializableChunks.length);
            for (RichSerializableChunk sC : serializableChunks) {
                MempoolChunk chunk =  sC.fromSerializableChunk();
                chunks.add(chunk);
            }
            return chunks;
        }
        return emptyList();
    }

}
