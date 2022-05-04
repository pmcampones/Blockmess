package catecoin.blockConstructors;

import catecoin.mempoolManager.MempoolManager;
import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.security.KeyPair;
import java.util.Properties;

public class BlockDirectorKits {

    public static <E extends IndexableContent, P extends SybilElectionProof>
    PrototypicalBlockDirector<E, BlockContent<E>, LedgerBlock<BlockContent<E>, P>, P>
    getSimpleLedgerBlock(Properties props, MempoolManager<E,P> mempoolManager, KeyPair proposer) {
        PrototypicalContentStorage<E> contentStorage = new ContextAwareContentStorage<>(props, mempoolManager);
        var contentBuilder = new SimpleBlockContentListBuilder<E>();
        var blockBuilder = new LedgerBlockBuilder<BlockContent<E>, P>(new ComputeUniformWeight<>(), proposer);
        return new SimpleBlockDirector<>(contentStorage, contentBuilder, blockBuilder, proposer);
    }

}
