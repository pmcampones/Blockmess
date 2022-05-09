package catecoin.blocks;

import catecoin.txs.IndexableContent;
import io.netty.buffer.ByteBuf;
import ledger.ledgerManager.StructuredValue;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.merkleTree.MerkleRoot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ContentList extends ProtoPojoAbstract {

    public static final short ID = 3920;

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo protoPojo, ByteBuf out) throws IOException {
            ContentList ContentListList = (ContentList) protoPojo;
            out.writeInt(ContentListList.contentLst.size());
            for (IndexableContent elem : ContentListList.contentLst)
                serializeElement(elem, out);
        }

        private void serializeElement(IndexableContent elem, ByteBuf out) throws IOException {
            out.writeShort(elem.getClassId());
            elem.getSerializer().serialize(elem, out);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) throws IOException {
            int numElems = in.readInt();
            List<StructuredValue> contentLst = new ArrayList<>(numElems);
            for (int i = 0; i < numElems; i++)
                contentLst.add((StructuredValue) deserializeElem(in));
            return new ContentList(contentLst);
        }

        private IndexableContent deserializeElem(ByteBuf in) throws IOException {
            short elemClass = in.readShort();
            ISerializer<ProtoPojo> serializer = ProtoPojo.pojoSerializers.get(elemClass);
            return (IndexableContent) serializer.deserialize(in);
        }
    };
    private final List<StructuredValue> contentLst;

    public boolean hasValidSemantics() {
        return contentLst.stream()
                .allMatch(IndexableContent::hasValidSemantics);
    }

    public ContentList(List<StructuredValue> contentLst) {
        super(ID);
        this.contentLst = contentLst;
    }

    public byte[] getContentHash() {
        List<byte[]> contentHashes = contentLst.stream()
                .map(IndexableContent::getHashVal)
                .collect(Collectors.toList());
        return new MerkleRoot(contentHashes).getHashValue();
    }

    public List<StructuredValue> getContentList() {
        return contentLst;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public int getSerializedSize() throws IOException {
        int accum = 0;
        for (StructuredValue elem : contentLst)
            accum += elem.getSerializedSize();
        return accum;
    }

}
