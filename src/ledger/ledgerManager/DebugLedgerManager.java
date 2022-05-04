package ledger.ledgerManager;

import catecoin.txs.IndexableContent;
import ledger.DebugLedger;
import ledger.blocks.BlockContent;
import ledger.blocks.BlockmessBlock;
import ledger.ledgerManager.nodes.BlockmessChain;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public interface DebugLedgerManager<E extends IndexableContent, C extends BlockContent<StructuredValue<E>>, P extends SybilElectionProof>
        extends DebugLedger<BlockmessBlock<C,P>> {

    Map<UUID,BlockmessChain<E,C,P>> getChains();

    BlockmessChain<E,C,P> getOrigin();

}
