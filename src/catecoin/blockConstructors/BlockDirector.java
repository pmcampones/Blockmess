package catecoin.blockConstructors;

import catecoin.txs.IndexableContent;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import sybilResistantCommitteeElection.SybilElectionProof;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Set;
import java.util.UUID;

public interface BlockDirector<E extends IndexableContent, C extends BlockContent<E>, B extends LedgerBlock<C,P>,
        P extends SybilElectionProof>
        extends ContentStorage<E> {

    B createBlockProposal(Set<UUID> previousStates, P proof)
            throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException;

    C createBlockContent(Set<UUID> previousStates, int usedSpace) throws IOException;

}
