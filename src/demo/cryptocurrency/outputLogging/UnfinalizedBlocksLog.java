package demo.cryptocurrency.outputLogging;

import com.opencsv.CSVWriter;
import ledger.blocks.BlockmessBlock;
import lombok.SneakyThrows;
import main.GlobalProperties;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class UnfinalizedBlocksLog {

	private static final String UNFINALIZED_BLOCKS_OUTPUT_FILE = "outputLogs/unfinalizedBlocks.csv";

	private final Path outputPath;

	private final boolean isRecording;

	@SneakyThrows
	public UnfinalizedBlocksLog() {
		Properties props = GlobalProperties.getProps();
		this.outputPath = Path.of(props.getProperty("unfinalizedBlocksOutputFile", UNFINALIZED_BLOCKS_OUTPUT_FILE));
		this.isRecording = props.getProperty("isRecordingUnfinalizedBlocks", "F").equals("T");
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
			csvWriter.writeNext(new String[]{"BlockID", "Num Txs", "Block Size", "Arrival Time"});
		}
	}

	@SneakyThrows
	public void logUnfinalizedBlock(BlockmessBlock block) {
		if (isRecording) {
			File file = new File(outputPath.toUri());
			try (var fWriter = new FileWriter(file, true); var csvWriter = new CSVWriter(fWriter)) {
				String blockId = block.getBlockId().toString();
				int numTxs = block.getContentList().getContentList().size();
				int blockSize = block.getSerializedSize();
				long arrivalTime = System.currentTimeMillis();
				csvWriter.writeNext(new String[]{blockId, String.valueOf(numTxs),
						String.valueOf(blockSize), String.valueOf(arrivalTime)});
			}
		}
	}

}
