package catecoin.validators;

import catecoin.mempoolManager.MempoolManager;
import catecoin.posSpecific.accountManagers.AccountManager;
import catecoin.posSpecific.accountManagers.StatefulAccountManager;
import catecoin.txs.SlimTransaction;
import ledger.blocks.BlockContent;
import ledger.blocks.LedgerBlock;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import sybilResistantCommitteeElection.SybilElectionProof;
import sybilResistantCommitteeElection.poet.drand.PoETDRandProof;
import sybilResistantCommitteeElection.pos.sortition.proofs.SortitionProof;

import java.util.Properties;

public class BlockValidatorFactory {

    public static <E extends SybilElectionProof> BlockValidator<LedgerBlock<BlockContent<SlimTransaction>, PoETDRandProof>> getPoETValidator(Properties props, MempoolManager<SlimTransaction, PoETDRandProof> mempoolManager)
            throws HandlerRegistrationException {
        return props.getProperty("simplifiedTxs", "F").equals("F") ?
                new ContextObliviousValidator<>(props, mempoolManager, new PoETProofValidator(props))
                : new StatelessValidator<>(props, new PoETProofValidator(props));
    }

    public static BlockValidator<LedgerBlock<BlockContent<SlimTransaction>, SortitionProof>> sortitionValidator(Properties props, MempoolManager<SlimTransaction, SortitionProof> mempoolManager)
            throws HandlerRegistrationException {
        AccountManager accountManager = new StatefulAccountManager<>(props, mempoolManager);
        SybilProofValidator<SortitionProof> proofValidator = new SortitionProofValidator(props, accountManager);
        return props.getProperty("simplifiedTxs", "F").equals("F") ?
                new KeyAndMicroBlocksValidator(props, mempoolManager, proofValidator)
                : new StatelessValidator<>(props, proofValidator);
    }

}
