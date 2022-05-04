package sybilResistantCommitteeElection.poet;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.nodes.BlockmessChain;
import main.CryptographicUtils;
import sybilResistantCommitteeElection.poet.gpoet.BlockmessGPoETProof;

import java.nio.ByteBuffer;
import java.util.UUID;

public class GPoETChainSeed<E extends IndexableContent, C extends BlockContent<StructuredValue<E>>> {

    private final UUID ChainId;

    private final UUID prevBlock;

    private final C currContent;

    private final byte[] ChainSeed;

    private final BlockmessChain<E,C, BlockmessGPoETProof> Chain;

    public GPoETChainSeed(UUID ChainId, UUID prevBlock, C currContent, BlockmessChain<E,C, BlockmessGPoETProof> Chain) {
        this.ChainId = ChainId;
        this.prevBlock = prevBlock;
        this.currContent = currContent;
        this.Chain = Chain;
        ChainSeed = computeChainSeed();
    }

    private byte[] computeChainSeed() {
        return computeChainSeed(ChainId, currContent, prevBlock);
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
        return ChainId;
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

    public BlockmessChain<E,C, BlockmessGPoETProof> getChain() {
        return Chain;
    }

}
