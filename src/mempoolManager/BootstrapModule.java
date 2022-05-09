package mempoolManager;

import com.google.gson.Gson;
import main.GlobalProperties;
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

public class BootstrapModule {

    private static final Logger logger = LogManager.getLogger(BootstrapModule.class);

    public static List<MempoolChunk> getStoredChunks() {
        try {
            return tryToGetStoredChunks();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("Unable to bootstrap DL because: {}", e.getMessage());
        }
        return emptyList();
    }

    private static List<MempoolChunk> tryToGetStoredChunks() throws IOException,
            NoSuchAlgorithmException, InvalidKeySpecException {
        Properties props = GlobalProperties.getProps();
        boolean isBootstraped = props.getProperty("isBootstraped", "F").equals("T");
        if (isBootstraped) {
            String bootstrapFile = props.getProperty("bootstrapFile",
                    "./bootstrapContent/bootstrap.txt");
            String bootstrapJson = Files.readString(Path.of(bootstrapFile));
            MempoolChunk[] chunks = new Gson().fromJson(bootstrapJson, MempoolChunk[].class);
            return new ArrayList<>(chunks.length);
        }
        return emptyList();
    }

}
