package ledger.ledgerManager.nodes;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.ledgerManager.StructuredValue;
import sybilResistantElection.SybilElectionProof;

public interface InnerNode<E extends IndexableContent, C extends BlockContent<StructuredValue<E>>, P extends SybilElectionProof>
        extends BlockmessChain<E,C,P>, ParentTreeNode<E,C,P> {}
