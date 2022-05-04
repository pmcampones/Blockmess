package catecoin.blockConstructors;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import ledger.blocks.LedgerBlockImp;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.List;
import java.util.UUID;

public class LedgerBlockBuilder<C extends BlockContent<? extends IndexableContent>, P extends SybilElectionProof>
        implements BlockBuilder<LedgerBlock<C,P>, C, P> {

    private final BlockWeightComputer<P> weightComputer;

    private final KeyPair self;

    public LedgerBlockBuilder(BlockWeightComputer<P> weightComputer, KeyPair self) {
        this.weightComputer = weightComputer;
        this.self = self;
    }

    @Override
    public LedgerBlock<C, P> buildBlock(C blockContent, P proof, List<UUID> prevRefs) throws IOException,
            NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        int inherentWeight = weightComputer.computeBlockWeight();
        return new LedgerBlockImp<>(inherentWeight, prevRefs, blockContent, proof, self);
    }
}
