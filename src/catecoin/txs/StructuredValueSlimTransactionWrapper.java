package catecoin.txs;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import ledger.ledgerManager.AppContent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class StructuredValueSlimTransactionWrapper {

    public static AppContent wrapTx(Transaction tx) {
        try {
            return tryToWrapTx(tx);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static AppContent tryToWrapTx(Transaction tx) throws IOException {
        ByteBuf buff = Unpooled.buffer();
        Transaction.serializer.serialize(tx, buff);
        byte[] fakeTx = new byte[200];
        buff.readBytes(fakeTx);
        return new AppContent(fakeTx);
    }

}
