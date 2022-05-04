package catecoin.validators;

import catecoin.blocks.chunks.FutureProofChunks;
import catecoin.blocks.chunks.MempoolChunk;
import catecoin.blocks.chunks.RichMempoolChunk;
import catecoin.mempoolManager.MempoolManager;
import catecoin.txs.SlimTransaction;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.SybilElectionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.KeyBlockReferencing;
import sybilResistantCommitteeElection.pos.sortition.proofs.KeyBlockSortitionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.MicroBlockSortitionProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.SortitionProof;
import utils.IDGenerator;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * This validator extends the {@link ContextObliviousValidator}
 * to make validations where the order of blocks are necessary.
 * <p>In particular, this validator ensures that all blocks
 * (which should be either microblocks or keyblocks) reference the correct keyblock.</p>
 */
public class KeyAndMicroBlocksValidator extends ContextObliviousValidator<SortitionProof> {

    public static final short ID = IDGenerator.genId();

    public KeyAndMicroBlocksValidator(Properties props, MempoolManager<SlimTransaction, SortitionProof> mempoolManager, SybilProofValidator<SortitionProof> proofValidator)
            throws HandlerRegistrationException {
        super(props, mempoolManager, proofValidator, ID);
    }

    @Override
    public boolean receivedValid(LedgerBlock<BlockContent<SlimTransaction>, SortitionProof> block) {
        return super.receivedValid(block)
                && areReferencesValid(block)
                && isMicroblockWithCorrectProposer(block);
    }

    private boolean areReferencesValid(LedgerBlock<BlockContent<SlimTransaction>, SortitionProof> block) {
        SybilElectionProof proof = block.getSybilElectionProof();
        if (!(proof instanceof KeyBlockReferencing))
            return false;
        KeyBlockReferencing keyRefRetriever = (KeyBlockReferencing) proof;
        UUID keyRef = keyRefRetriever.getAssociatedKeyBlock();
        List<UUID> prevBlocks = block.getPrevRefs();
        for (UUID prevBlockId : prevBlocks) {
            MempoolChunk prevBlock = mempoolManager.mempool.get(prevBlockId);
            if (!prevBlockId.equals(new UUID(0,0))) {   //TODO GENESIS BLOCK NEEDS A SPECIAL CASE. THIS WILL NOT WORK IF THE DL IS BOOTSTRAPPED
                if (!(prevBlock instanceof FutureProofChunks))
                    return false;
                Optional<UUID> prevKeyRef = getRefFromId((FutureProofChunks) prevBlock);
                if (prevKeyRef.isEmpty() || !keyRef.equals(prevKeyRef.get()))
                    return false;
            }
        }
        return true;
    }

    private Optional<UUID> getRefFromId(FutureProofChunks block) {
        SybilElectionProof proof = block.getProof();
        if (proof instanceof MicroBlockSortitionProof) {
            UUID keyRef = ((MicroBlockSortitionProof) proof).getAssociatedKeyBlock();
            return Optional.of(keyRef);
        } else if (proof instanceof KeyBlockSortitionProof) {
            return Optional.of(block.getId());
        } else {
            return Optional.empty();
        }
    }

    private boolean isMicroblockWithCorrectProposer(LedgerBlock<BlockContent<SlimTransaction>, SortitionProof> block) {
        SybilElectionProof proof = block.getSybilElectionProof();
        if (!(proof instanceof MicroBlockSortitionProof))
            return true;
        List<UUID> prevLst = block.getPrevRefs();
        if (prevLst.size() != 1)
            return false;
        UUID prev = prevLst.get(0);
        MempoolChunk prevChunk = mempoolManager.mempool.get(prev);
        if (!(prevChunk instanceof RichMempoolChunk))
            return false;
        RichMempoolChunk richChunk = (RichMempoolChunk) prevChunk;
        return block.getProposer().equals(richChunk.getProposer());
    }
}
