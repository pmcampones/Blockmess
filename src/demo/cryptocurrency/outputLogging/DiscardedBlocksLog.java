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

public class DiscardedBlocksLog {

	private static final String DISCARDED_BLOCKS_OUTPUT_FILE = "outputLogs/discardedBlocks.csv";

	private final Path outputPath;

	private final boolean isRecording;

	@SneakyThrows
	public DiscardedBlocksLog() {
		Properties props = GlobalProperties.getProps();
		this.outputPath = Path.of(props.getProperty("discardedBlocksOutputFile",
				DISCARDED_BLOCKS_OUTPUT_FILE));
		this.isRecording = props.getProperty("isRecordingDiscardedBlocks", "F").equals("T");
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
			csvWriter.writeNext(new String[]{"BlockID", "Discard Time"});
		}
	}

	@SneakyThrows
	public void logDiscardedBlock(Collection<UUID> discardedBlocks) {
		if (isRecording) {
			File file = new File(outputPath.toUri());
			try (var fWriter = new FileWriter(file, true); var csvWriter = new CSVWriter(fWriter)) {
				String discardTime = String.valueOf(System.currentTimeMillis());
				List<String[]> logs = discardedBlocks.stream().map(UUID::toString)
						.map(id -> new String[]{id, discardTime}).collect(Collectors.toList());
				csvWriter.writeAll(logs);
			}
		}
	}

}
