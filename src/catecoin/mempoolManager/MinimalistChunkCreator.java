package catecoin.mempoolManager;

import catecoin.blocks.chunks.MempoolChunk;
import catecoin.blocks.chunks.MinimalistMempoolChunk;
import catecoin.txs.SlimTransaction;
import catecoin.utxos.StorageUTXO;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toUnmodifiableSet;

public class MinimalistChunkCreator<P extends SybilElectionProof> implements MempoolChunkCreator<SlimTransaction,P> {

    @Override
    public MempoolChunk createChunk(LedgerBlock<BlockContent<SlimTransaction>, P> block,
                                    int cumulativeWeight) {
        BlockContent<SlimTransaction> blockContent = block.getBlockContent();
        Set<StorageUTXO> addedUtxos = extractAddedUtxos(blockContent);
        Set<UUID> usedUtxos = extractRemovedUtxos(blockContent);
        Set<UUID> usedTxs = extractUsedTxs(blockContent);
        return new MinimalistMempoolChunk(block.getBlockId(), Set.copyOf(block.getPrevRefs()),
                unmodifiableSet(addedUtxos), usedUtxos, usedTxs, cumulativeWeight);
    }

    private Set<StorageUTXO> extractAddedUtxos(BlockContent<SlimTransaction> blockContent) {
        return blockContent.getContentList().parallelStream().flatMap(tx -> Stream.concat(
                tx.getOutputsDestination().stream()
                        .map(u -> new StorageUTXO(u.getId(), u.getAmount(), tx.getDestination())),
                tx.getOutputsOrigin().stream()
                        .map(u -> new StorageUTXO(u.getId(), u.getAmount(), tx.getOrigin()))
                )
        ).collect(toUnmodifiableSet());
    }

    private Set<UUID> extractRemovedUtxos(BlockContent<SlimTransaction> blockContent) {
        return blockContent.getContentList()
                .stream()
                .flatMap(tx -> tx.getInputs().stream())
                .collect(toUnmodifiableSet());
    }

    private Set<UUID> extractUsedTxs(BlockContent<SlimTransaction> blockContent) {
        return blockContent.getContentList()
                .stream()
                .map(SlimTransaction::getId)
                .collect(toUnmodifiableSet());
    }

}
