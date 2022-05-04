package catecoin.blocks;

import catecoin.txs.IndexableContent;
import io.netty.buffer.ByteBuf;
import ledger.blocks.BlockContent;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.merkleTree.MerkleRoot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BlockContentList<E extends IndexableContent>
        extends ProtoPojoAbstract implements BlockContent<E> {

    public static final short ID = 3920;

    private final List<E> contentLst;

    public BlockContentList(List<E> contentLst) {
        super(ID);
        this.contentLst = contentLst;
    }

    @Override
    public boolean hasValidSemantics() {
        return contentLst.stream()
                .allMatch(IndexableContent::hasValidSemantics);
    }

    @Override
    public List<E> getContentList() {
        return contentLst;
    }

    @Override
    public byte[] getContentHash() {
        List<byte[]> contentHashes = contentLst.stream()
                .map(IndexableContent::getHashVal)
                .collect(Collectors.toList());
        return new MerkleRoot(contentHashes).getHashValue();
    }

    @Override
    public int getSerializedSize() throws IOException {
        int accum = 0;
        for (E elem : contentLst)
            accum += elem.getSerializedSize();
        return accum;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo protoPojo, ByteBuf out) throws IOException {
            BlockContentList<IndexableContent> blockContentList = (BlockContentList) protoPojo;
            out.writeInt(blockContentList.contentLst.size());
            for (IndexableContent elem : blockContentList.contentLst)
                serializeElement(elem, out);
        }

        private void serializeElement(IndexableContent elem, ByteBuf out) throws IOException {
            out.writeShort(elem.getClassId());
            elem.getSerializer().serialize(elem, out);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) throws IOException {
            int numElems = in.readInt();
            List<IndexableContent> contentLst = new ArrayList<>(numElems);
            for (int i = 0; i < numElems; i++)
                contentLst.add(deserializeElem(in));
            return new BlockContentList<>(contentLst);
        }

        private IndexableContent deserializeElem(ByteBuf in) throws IOException {
            short elemClass = in.readShort();
            ISerializer<ProtoPojo> serializer = ProtoPojo.pojoSerializers.get(elemClass);
            return (IndexableContent) serializer.deserialize(in);
        }
    };

}
