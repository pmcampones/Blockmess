package catecoin.blockConstructors;

import catecoin.blocks.SimpleBlockContentList;
import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import main.CryptographicUtils;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Generates blocks whose content is a {@link SimpleBlockContentList} with any kind of content.
 * <p>In practice, this is the only kind of block constructors we use, however, their internal workings
 * depend also on the {@link ContentStorage} passed as parameter.</p>
 * <p>If all content in a block is of the same kind (which is the case on our default application)
 * then the block content could be optimized to improve serialization speed. We focus on other optimizations,
 * as the serialization of blocks is not the major bottleneck.</p>
 */
public class SimpleBlockDirector<E extends IndexableContent, C extends BlockContent<E>,
        B extends LedgerBlock<C,P>, P extends SybilElectionProof>
        implements PrototypicalBlockDirector<E,C,B,P> {

    private final PrototypicalContentStorage<E> contentStorage;

    private final BlockContentBuilder<E,C> contentBuilder;

    private final BlockBuilder<B,C,P> blockBuilder;

    private final KeyPair self;

    public SimpleBlockDirector(PrototypicalContentStorage<E> contentStorage, BlockContentBuilder<E, C> contentBuilder,
                               BlockBuilder<B, C, P> blockBuilder, KeyPair self) {
        this.contentStorage = contentStorage;
        this.contentBuilder = contentBuilder;
        this.blockBuilder = blockBuilder;
        this.self = self;
    }

    @Override
    public B createBlockProposal(Set<UUID> previousStates, P proof)
            throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        int usedSpace = previousStates.size() * 2 * Long.BYTES
                + proof.getSerializedSize()
                + CryptographicUtils.computeKeySize(self.getPublic());
        List<E> txs = generateBlockContentList(previousStates, usedSpace);
        C blockContent = contentBuilder.buildContent(txs);
        return blockBuilder.buildBlock(blockContent, proof, List.copyOf(previousStates));
    }

    @Override
    public B createBoundBlockProposal(Set<UUID> previousStates, P proof, int maxTxs)
            throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        int usedSpace = previousStates.size() * 2 * Long.BYTES
                + proof.getSerializedSize()
                + CryptographicUtils.computeKeySize(self.getPublic());
        List<E> txs = generateBoundBlockContentList(previousStates, usedSpace, maxTxs);
        C blockContent = contentBuilder.buildContent(txs);
        return blockBuilder.buildBlock(blockContent, proof, List.copyOf(previousStates));
    }

    @Override
    public C createBlockContent(Set<UUID> previousStates, int usedSpace) throws IOException {
        return contentBuilder.buildContent(generateBlockContentList(previousStates, usedSpace));
    }

    @Override
    public List<E> generateBlockContentList(Collection<UUID> states, int usedSpace) throws IOException {
        return contentStorage.generateBlockContentList(states, usedSpace);
    }

    @Override
    public List<E> generateBoundBlockContentList(Collection<UUID> states, int usedSpace, int maxTxs)
            throws IOException {
        return contentStorage.generateBoundBlockContentList(states, usedSpace, maxTxs);
    }

    @Override
    public void submitContent(Collection<E> content) {
        contentStorage.submitContent(content);
    }

    @Override
    public void submitContent(E content) {
        contentStorage.submitContent(content);
    }

    @Override
    public void deleteContent(Set<UUID> contentIds) {
        contentStorage.deleteContent(contentIds);
    }

    @Override
    public Collection<E> getStoredContent() {
        return contentStorage.getStoredContent();
    }

    @Override
    public void halveChainThroughput() {
        contentStorage.halveChainThroughput();
    }

    @Override
    public void doubleChainThroughput() {
        contentStorage.doubleChainThroughput();
    }

    @Override
    public int getThroughputReduction() {
        return contentStorage.getThroughputReduction();
    }

    @Override
    public void setChainThroughputReduction(int reduction) {
        contentStorage.setChainThroughputReduction(reduction);
    }

    @Override
    public PrototypicalBlockDirector<E,C,B,P> clonePrototype() {
        return new SimpleBlockDirector<>(contentStorage.clonePrototype(), contentBuilder, blockBuilder, self);
    }
}
