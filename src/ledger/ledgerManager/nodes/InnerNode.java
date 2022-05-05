package ledger.ledgerManager.nodes;

import catecoin.blocks.ContentList;
import catecoin.txs.Transaction;
import ledger.ledgerManager.StructuredValue;
import sybilResistantElection.SybilResistantElectionProof;

public interface InnerNode
        extends BlockmessChain, ParentTreeNode<Transaction,ContentList<StructuredValue<Transaction>>,SybilResistantElectionProof> {}
