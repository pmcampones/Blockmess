package main;

import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import utils.IDGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static java.nio.file.StandardOpenOption.APPEND;

public class ChangesInNumberOfChainsLog {

    public static final short ID = IDGenerator.genId();

    private static final String CHANGES_CHAINS_OUTPUT_FILE = "outputLogs/changesChains";

    private final Path changesChainsOutputFile;

    public ChangesInNumberOfChainsLog(Properties props) throws IOException, HandlerRegistrationException {
        changesChainsOutputFile = Path.of(props.getProperty("changesChainsOutputFile",
                CHANGES_CHAINS_OUTPUT_FILE));
        Files.deleteIfExists(changesChainsOutputFile);
        Files.createDirectories(changesChainsOutputFile.getParent());
        Files.createFile(changesChainsOutputFile);
        Files.writeString(changesChainsOutputFile, "", APPEND);
    }

    public void logChangeInNumChains(int numChains) {
        long now = System.currentTimeMillis();
        try {
            Files.writeString(changesChainsOutputFile,
                    String.format("%d -> %d\n", numChains, now), APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
