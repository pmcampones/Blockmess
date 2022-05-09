package ledger.ledgerManager;

import blockConstructors.CMuxMask;
import broadcastProtocols.BroadcastValue;
import broadcastProtocols.BroadcastValueAbstract;
import catecoin.txs.IndexableContent;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.CryptographicUtils;

import java.util.UUID;

public class AppContent extends BroadcastValueAbstract implements IndexableContent {

    public static final short ID = 1982;

    public static final ISerializer<BroadcastValue> serializer = new ISerializer<>() {

        @Override
        public void serialize(BroadcastValue broadcastValue, ByteBuf out) {
            AppContent appContent = (AppContent) broadcastValue;
            out.writeShort(appContent.getSerializedSize());
            out.writeBytes(appContent.content);
        }

        @Override
        public BroadcastValue deserialize(ByteBuf in) {
            byte[] content = new byte[in.readShort()];
            in.readBytes(content);
            return new AppContent(content);
        }
    };
    private final transient UUID id;
    private transient final byte[] hashVal, cmuxId1, cmuxId2;
    private final transient CMuxMask mask = new CMuxMask();
    private final byte[] content;

    public AppContent(byte[] content) {
        super(ID);
        this.hashVal = CryptographicUtils.hashInput(content);
        this.id = CryptographicUtils.generateUUIDFromBytes(hashVal);
        this.cmuxId1 = CryptographicUtils.hashInput(hashVal);
        this.cmuxId2 = CryptographicUtils.hashInput(cmuxId1);
        this.content = content;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public byte[] getHashVal() {
        return hashVal;
    }

    @Override
    public boolean hasValidSemantics() {
        return true;
    }

    public byte[] getCmuxId1() {
        return cmuxId1;
    }

    public byte[] getCmuxId2() {
        return cmuxId2;
    }

    public CMuxMask.MaskResult matchIds() {
        return mask.matchIds(cmuxId1, cmuxId2);
    }

    public void advanceMask() {
        mask.advanceMask();
    }

    @Override
    public ISerializer<BroadcastValue> getSerializer() {
        return serializer;
    }

    @Override
    public int getSerializedSize() {
        return content.length;
    }
}
