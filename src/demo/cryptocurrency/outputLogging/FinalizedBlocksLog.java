package demo.cryptocurrency.outputLogging;

import applicationInterface.GlobalProperties;
import com.opencsv.CSVWriter;
import lombok.SneakyThrows;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

	private final boolean isRecording;

	@SneakyThrows
	public FinalizedBlocksLog() {
		Properties props = GlobalProperties.getProps();
		this.outputPath = Path.of(props.getProperty("finalizedBlocksOutputFile", FINALIZED_BLOCKS_OUTPUT_FILE));
		this.isRecording = props.getProperty("isRecordingFinalizedBlocks", "F").equals("T");
		if (isRecording)
			setupOutputFile();
	}

	private void setupOutputFile() throws IOException {
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
		if (isRecording) {
			File file = new File(outputPath.toUri());
			try (var fWriter = new FileWriter(file, true); var csvWriter = new CSVWriter(fWriter)) {
				String finalizationTime = String.valueOf(System.currentTimeMillis());
				List<String[]> logs = finalizedBlocks.stream().map(UUID::toString)
						.map(id -> new String[]{id, finalizationTime}).collect(Collectors.toList());
				csvWriter.writeAll(logs);
			}
		}
	}

}
