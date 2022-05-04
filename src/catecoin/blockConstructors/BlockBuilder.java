package catecoin.blockConstructors;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.List;
import java.util.UUID;

public interface BlockBuilder<B extends LedgerBlock<C,P>, C extends BlockContent<? extends IndexableContent>,
        P extends SybilElectionProof> {

    B buildBlock(C blockContent, P proof, List<UUID> prevRefs)
            throws IOException, NoSuchAlgorithmException, SignatureException, InvalidKeyException;

}
