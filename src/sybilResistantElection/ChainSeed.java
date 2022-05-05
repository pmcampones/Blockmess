package sybilResistantElection;

import catecoin.blocks.ContentList;
import catecoin.txs.IndexableContent;
import catecoin.txs.Transaction;
import ledger.ledgerManager.StructuredValue;
import ledger.ledgerManager.nodes.BlockmessChain;
import utils.CryptographicUtils;

import java.nio.ByteBuffer;
import java.util.UUID;

public class ChainSeed {

    private final UUID chainId;

    private final UUID prevBlock;

    private final ContentList<StructuredValue<Transaction>> currContent;

    private final byte[] chainSeed;

    private final BlockmessChain<Transaction> chain;

    public ChainSeed(UUID chainId, UUID prevBlock, ContentList<StructuredValue<Transaction>> currContent, BlockmessChain<Transaction> chain) {
        this.chainId = chainId;
        this.prevBlock = prevBlock;
        this.currContent = currContent;
        this.chain = chain;
        chainSeed = computeChainSeed();
    }

    private byte[] computeChainSeed() {
        return computeChainSeed(chainId, currContent, prevBlock);
    }

    public static <C extends ContentList<? extends IndexableContent>> byte[] computeChainSeed(UUID ChainId, C currContent, UUID prevBlock) {
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

    public ContentList<StructuredValue<Transaction>> getCurrContent() {
        return currContent;
    }

    public byte[] getChainSeed() {
        return chainSeed;
    }

    public BlockmessChain<Transaction> getChain() {
        return chain;
    }

}
