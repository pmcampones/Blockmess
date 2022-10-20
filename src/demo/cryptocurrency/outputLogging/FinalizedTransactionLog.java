package demo.cryptocurrency.outputLogging;

import applicationInterface.GlobalProperties;
import com.opencsv.CSVWriter;
import demo.cryptocurrency.Transaction;
import lombok.SneakyThrows;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class FinalizedTransactionLog {

	private static final String FINALIZED_TRANSACTIONS_OUTPUT_FILE = "outputLogs/finalizedTransactions.csv";

	private final Path outputPath;

	private final boolean isRecording;

	@SneakyThrows
	public FinalizedTransactionLog() {
		Properties props = GlobalProperties.getProps();
		this.outputPath = Path.of(props.getProperty("finalizedTransactionsOutputFile",
				FINALIZED_TRANSACTIONS_OUTPUT_FILE));
		this.isRecording = props.getProperty("isRecordingFinalizedTransactions", "F").equals("T");
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
			csvWriter.writeNext(new String[]{"TxID", "Size", "Delivery Time"});
		}
	}

	@SneakyThrows
	public void logFinalizedTransaction(Transaction tx) {
		if (isRecording) {
			File file = new File(outputPath.toUri());
			try (var fWriter = new FileWriter(file, true); var csvWriter = new CSVWriter(fWriter)) {
				String txId = tx.getId().toString();
				String txSize = String.valueOf(tx.getSerializedSize());
				long arrivalTime = System.currentTimeMillis();
				csvWriter.writeNext(new String[]{txId, txSize, String.valueOf(arrivalTime)});
			}
		}
	}

}
