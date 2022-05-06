package catecoin.mempoolManager;

import catecoin.blocks.ContentList;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.blocks.chunks.MinimalistMempoolChunk;
import catecoin.txs.Transaction;
import catecoin.utxos.StorageUTXO;
import ledger.blocks.LedgerBlock;
import ledger.ledgerManager.StructuredValue;
import sybilResistantElection.SybilResistantElectionProof;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableSet;

public class ChunkCreator {

    public MempoolChunk createChunk(LedgerBlock<ContentList<StructuredValue<Transaction>>, SybilResistantElectionProof> block, int cumulativeWeight) {
        List<Transaction> unwrappedContent = block.getContentList()
                .getContentList()
                .stream()
                .map(StructuredValue::getInnerValue)
                .collect(toList());
        Set<StorageUTXO> addedUtxos = extractAddedUtxos(unwrappedContent);
        Set<UUID> usedUtxos = extractRemovedUtxos(unwrappedContent);
        Set<UUID> usedTxs = extractUsedTxs(unwrappedContent);
        return new MinimalistMempoolChunk(block.getBlockId(), Set.copyOf(block.getPrevRefs()),
                unmodifiableSet(addedUtxos), usedUtxos, usedTxs, cumulativeWeight);
    }
    
    private Set<StorageUTXO> extractAddedUtxos(List<Transaction> ContentList) {
        return ContentList.parallelStream().flatMap(tx -> Stream.concat(
                        tx.getOutputsDestination().stream()
                                .map(u -> new StorageUTXO(u.getId(), u.getAmount(), tx.getDestination())),
                        tx.getOutputsOrigin().stream()
                                .map(u -> new StorageUTXO(u.getId(), u.getAmount(), tx.getOrigin()))
                )
        ).collect(toUnmodifiableSet());
    }

    private Set<UUID> extractRemovedUtxos(List<Transaction> ContentList) {
        return ContentList.stream()
                .flatMap(tx -> tx.getInputs().stream())
                .collect(toUnmodifiableSet());
    }

    private Set<UUID> extractUsedTxs(List<Transaction> ContentList) {
        return ContentList.stream()
                .map(Transaction::getId)
                .collect(toUnmodifiableSet());
    }
}
