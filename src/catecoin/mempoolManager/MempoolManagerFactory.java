package catecoin.mempoolManager;

import catecoin.txs.IndexableContent;
import catecoin.txs.SlimTransaction;
import sybilResistantCommitteeElection.SybilElectionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.SortitionProof;

import java.util.Properties;

public class MempoolManagerFactory {

    public static <P extends SybilElectionProof> MempoolManager<SlimTransaction,P> getMinimalistMempoolManager(
            Properties props) throws Exception {
        return new MempoolManager<>(props, new MinimalistChunkCreator<>(),
                new MinimalistRecordModule(props), new MinimalistBootstrapModule());
    }

    public static <E extends IndexableContent> MempoolManager<SlimTransaction,SortitionProof> getRichMempoolManager(
            Properties props) throws Exception {
        return new MempoolManager<>(props, new RichChunkCreator(),
                new RichRecordModule(props), new RichBootstrapModule());
    }

}
