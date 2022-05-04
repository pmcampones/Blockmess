package ledger.ledgerManager;

import catecoin.blockConstructors.StructuredValueMask;
import catecoin.txs.IndexableContent;
import io.netty.buffer.ByteBuf;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class StructuredValue<E extends IndexableContent> extends ProtoPojoAbstract implements IndexableContent {

    public static final short ID = 1982;

    private final byte[] match1, match2;

    private final StructuredValueMask mask = new StructuredValueMask();

    private final E innerValue;

    public StructuredValue(byte[] match1, byte[] match2, E innerValue) {
        super(ID);
        this.match1 = match1;
        this.match2 = match2;
        this.innerValue = innerValue;
    }

    @Override
    public UUID getId() {
        return innerValue.getId();
    }

    @Override
    public byte[] getHashVal() {
        return innerValue.getHashVal();
    }

    @Override
    public boolean hasValidSemantics() {
        return innerValue.hasValidSemantics();
    }

    public byte[] getMatch1() {
        return match1;
    }

    public byte[] getMatch2() {
        return match2;
    }

    public E getInnerValue() {
        return innerValue;
    }

    public StructuredValueMask.MaskResult matchIds() {
        return mask.matchIds(match1, match2);
    }

    public void advanceMask() {
        mask.advanceMask();
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo protoPojo, ByteBuf out) throws IOException {
            StructuredValue structuredValue = (StructuredValue) protoPojo;
            serializeMatch(structuredValue.match1, out);
            serializeMatch(structuredValue.match2, out);
            serializeInnerPojo(structuredValue.innerValue, out);
        }

        private void serializeMatch(byte[] match, ByteBuf out) {
            out.writeShort(match.length);
            out.writeBytes(match);
        }

        private void serializeInnerPojo(ProtoPojo inner, ByteBuf out) throws IOException {
            out.writeShort(inner.getClassId());
            inner.getSerializer().serialize(inner, out);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) throws IOException {
            byte[] match1 = deserializeMatch(in);
            byte[] match2 = deserializeMatch(in);
            IndexableContent inner = (IndexableContent) deserializeInner(in);
            return new StructuredValue<>(match1, match2, inner);
        }

        private byte[] deserializeMatch(ByteBuf in) {
            byte[] match = new byte[in.readShort()];
            in.readBytes(match);
            return match;
        }

        private ProtoPojo deserializeInner(ByteBuf in) throws IOException {
            short innerId = in.readShort();
            ISerializer<ProtoPojo> serializer = ProtoPojo.pojoSerializers.get(innerId);
            return serializer.deserialize(in);
        }

     };

    @Override
    public int getSerializedSize() throws IOException {
        return innerValue.getSerializedSize()
                + (match1.length + match2.length) * Byte.BYTES;
    }
}
