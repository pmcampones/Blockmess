package catecoin.mempoolManager;

import catecoin.blocks.ContentList;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.blocks.chunks.MinimalistMempoolChunk;
import catecoin.txs.Transaction;
import catecoin.utxos.StorageUTXO;
import ledger.blocks.LedgerBlock;
import sybilResistantElection.SybilResistantElectionProof;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toUnmodifiableSet;

public class MinimalistChunkCreator{

    public MempoolChunk createChunk(LedgerBlock<ContentList<Transaction>, SybilResistantElectionProof> block,
                                    int cumulativeWeight) {
        ContentList<Transaction> ContentList = block.getContentList();
        Set<StorageUTXO> addedUtxos = extractAddedUtxos(ContentList);
        Set<UUID> usedUtxos = extractRemovedUtxos(ContentList);
        Set<UUID> usedTxs = extractUsedTxs(ContentList);
        return new MinimalistMempoolChunk(block.getBlockId(), Set.copyOf(block.getPrevRefs()),
                unmodifiableSet(addedUtxos), usedUtxos, usedTxs, cumulativeWeight);
    }

    private Set<StorageUTXO> extractAddedUtxos(ContentList<Transaction> ContentList) {
        return ContentList.getContentList().parallelStream().flatMap(tx -> Stream.concat(
                tx.getOutputsDestination().stream()
                        .map(u -> new StorageUTXO(u.getId(), u.getAmount(), tx.getDestination())),
                tx.getOutputsOrigin().stream()
                        .map(u -> new StorageUTXO(u.getId(), u.getAmount(), tx.getOrigin()))
                )
        ).collect(toUnmodifiableSet());
    }

    private Set<UUID> extractRemovedUtxos(ContentList<Transaction> ContentList) {
        return ContentList.getContentList()
                .stream()
                .flatMap(tx -> tx.getInputs().stream())
                .collect(toUnmodifiableSet());
    }

    private Set<UUID> extractUsedTxs(ContentList<Transaction> ContentList) {
        return ContentList.getContentList()
                .stream()
                .map(Transaction::getId)
                .collect(toUnmodifiableSet());
    }

}
