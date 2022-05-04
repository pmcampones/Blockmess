package sybilResistantElection;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.nodes.BlockmessChain;
import utils.CryptographicUtils;

import java.nio.ByteBuffer;
import java.util.UUID;

public class ChainSeed<E extends IndexableContent, C extends BlockContent<StructuredValue<E>>> {

    private final UUID chainId;

    private final UUID prevBlock;

    private final C currContent;

    private final byte[] ChainSeed;

    private final BlockmessChain<E,C, SybilResistantElectionProof> Chain;

    public ChainSeed(UUID chainId, UUID prevBlock, C currContent, BlockmessChain<E,C, SybilResistantElectionProof> Chain) {
        this.chainId = chainId;
        this.prevBlock = prevBlock;
        this.currContent = currContent;
        this.Chain = Chain;
        ChainSeed = computeChainSeed();
    }

    private byte[] computeChainSeed() {
        return computeChainSeed(chainId, currContent, prevBlock);
    }

    public static <C extends BlockContent<? extends IndexableContent>> byte[] computeChainSeed(UUID ChainId, C currContent, UUID prevBlock) {
        byte[] contentHash = currContent.getContentHash();
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 * Long.BYTES + contentHash.length * Byte.BYTES);
        byteBuffer.putLong(ChainId.getMostSignificantBits());
        byteBuffer.putLong(ChainId.getLeastSignificantBits());
        byteBuffer.putLong(prevBlock.getMostSignificantBits());
        byteBuffer.putLong(prevBlock.getLeastSignificantBits());
        byteBuffer.put(contentHash);
        return CryptographicUtils.hashInput(byteBuffer.array());
    }

    public UUID getChainId() {
        return chainId;
    }

    public UUID getPrevBlock() {
        return prevBlock;
    }

    public C getCurrContent() {
        return currContent;
    }

    public byte[] getChainSeed() {
        return ChainSeed;
    }

    public BlockmessChain<E,C, SybilResistantElectionProof> getChain() {
        return Chain;
    }

}
