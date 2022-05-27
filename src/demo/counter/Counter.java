package demo.counter;

import applicationInterface.ApplicationInterface;
import ledger.blocks.BlockmessBlock;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Counter extends ApplicationInterface {

    private static int counter = 0;

    public Counter(String[] args) {
        super(args);
    }

    static int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    static byte[] numToBytes(int num) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(num).array();
    }

    static String[] sliceArray(String[] array, int startIdx, int endIdx) {
        String[] slice = new String[endIdx - startIdx];
        System.arraycopy(array, startIdx, slice, 0, endIdx - startIdx);
        return slice;
    }

    @Override
    public byte[] processOperation(byte[] operation) {
        int change = bytesToInt(operation);
        counter += change;
        return numToBytes(counter);
    }

    @Override
    public void notifyNonFinalizedBlock(BlockmessBlock block) {
        System.out.printf("Block %s, referencing %s, in chain %s, carrying %d operations.\n",
                block.getBlockId(), block.getPrevRefs(), block.getDestinationChain(), block.getContentList().getContentList().size());
    }

    @Override
    public void notifyFinalizedBlocks(List<UUID> finalized, Set<UUID> discarded) {}

}
