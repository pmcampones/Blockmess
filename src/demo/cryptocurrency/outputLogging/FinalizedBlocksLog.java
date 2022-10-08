package demo.cryptocurrency.outputLogging;

import com.opencsv.CSVWriter;
import lombok.SneakyThrows;
import main.GlobalProperties;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

public class FinalizedBlocksLog {

	private static final String FINALIZED_BLOCKS_OUTPUT_FILE = "outputLogs/finalizedBlocks.csv";

	private final Path outputPath;

	@SneakyThrows
	public FinalizedBlocksLog() {
		Properties props = GlobalProperties.getProps();
		this.outputPath = Path.of(props.getProperty("finalizedBlocksOutputFile", FINALIZED_BLOCKS_OUTPUT_FILE));
		Files.deleteIfExists(outputPath);
		Files.createDirectories(outputPath.getParent());
		Files.createFile(outputPath);
		writeHeader();
	}

	@SneakyThrows
	private void writeHeader() {
		File file = new File(outputPath.toUri());
		try (var fWriter = new FileWriter(file); var csvWriter = new CSVWriter(fWriter)) {
			csvWriter.writeNext(new String[]{"BlockID", "Finalization Time"});
		}
	}

	@SneakyThrows
	public void logFinalizedBlock(Collection<UUID> finalizedBlocks) {
		File file = new File(outputPath.toUri());
		try (var fWriter = new FileWriter(file); var csvWriter = new CSVWriter(fWriter)) {
			String finalizationTime = String.valueOf(System.currentTimeMillis());
			List<String[]> logs = finalizedBlocks.stream().map(UUID::toString)
					.map(id -> new String[]{id, finalizationTime}).collect(Collectors.toList());
			csvWriter.writeAll(logs);
		}
	}

}
