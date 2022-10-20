package demo.cryptocurrency.outputLogging;

import applicationInterface.GlobalProperties;
import com.opencsv.CSVWriter;
import ledger.ledgerManager.ChainChangeObserver;
import lombok.SneakyThrows;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ChangesChainsLog implements ChainChangeObserver {

	private static final String CHANGES_CHAINS_OUTPUT_FILE = "outputLogs/changesChains.csv";

	private final Path outputPath;

	private final boolean isRecording;

	@SneakyThrows
	public ChangesChainsLog() {
		Properties props = GlobalProperties.getProps();
		this.outputPath = Path.of(props.getProperty("changesChainsOutputFile", CHANGES_CHAINS_OUTPUT_FILE));
		this.isRecording = props.getProperty("isRecordingChangesChains", "F").equals("T");
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
			csvWriter.writeNext(new String[]{"Number Active Chains", "Update Time"});
		}
	}

	@Override
	public void notifyChangesChains(int updatedNumChains) {
		if (isRecording)
			recordChangesChains(updatedNumChains);
	}

	@SneakyThrows
	private void recordChangesChains(int updatedNumChains) {
		File file = new File(outputPath.toUri());
		try (var fWriter = new FileWriter(file, true); var csvWriter = new CSVWriter(fWriter)) {
			String recordTime = String.valueOf(System.currentTimeMillis());
			csvWriter.writeNext(new String[]{String.valueOf(updatedNumChains), recordTime});
		}
	}

}
