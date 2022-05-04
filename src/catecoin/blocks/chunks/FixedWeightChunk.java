package catecoin.blocks.chunks;

import ledger.blockchain.Blockchain;

/**
 * Mempool chunk in DLs where the cumulative weight of blocks is constant.
 * <p>Notably, this happens in DLs using a {@link Blockchain} {@link ledger.Ledger} implementation,
 * with a Longest Chain Rule finalization procedure.</p>
 * <p>If other finalization rules are used, such as <a href=https://eprint.iacr.org/2013/881.pdf>GHOST</a> or PHANTOM,
 * the cumulative weight of a block changes as others are appended,
 * thus a query to the Ledger is necessary.</p>
 */
public interface FixedWeightChunk extends MempoolChunk {

    int getCumulativeWeight();

}
