package catecoin.mempoolManager;

import catecoin.blocks.chunks.MempoolChunk;

import java.util.List;
import java.util.Properties;

public interface BootstrapModule {

    List<MempoolChunk> getStoredChunks(Properties props);

}
