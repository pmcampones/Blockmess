package sybilResistantElection;

import broadcastProtocols.BroadcastValue;
import broadcastProtocols.BroadcastValueAbstract;
import io.netty.buffer.ByteBuf;
import ledger.blocks.SizeAccountable;
import org.apache.commons.lang3.tuple.Pair;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SybilResistantElectionProof extends BroadcastValueAbstract implements BroadcastValue, SizeAccountable {

    public static final short ID = 7265;

    private final List<Pair<UUID, byte[]>> ChainSeeds;

    private final int nonce;

    public SybilResistantElectionProof(List<Pair<UUID, byte[]>> ChainSeeds, int nonce) {
        super(ID);
        this.ChainSeeds = ChainSeeds;
        this.nonce = nonce;
    }

    public List<Pair<UUID, byte[]>> getChainSeeds() {
        return ChainSeeds;
    }

    public int getNonce() {
        return nonce;
    }

    @Override
    public int getSerializedSize() throws IOException {
        return Integer.BYTES + ChainSeeds.size() * 2 * Long.BYTES +
                ChainSeeds.stream()
                .map(Pair::getValue)
                .mapToInt(b -> b.length)
                .sum();
    }

    public static final ISerializer<BroadcastValue> serializer = new ISerializer<>() {

        @Override
        public void serialize(BroadcastValue broadcastValue, ByteBuf out) {
            SybilResistantElectionProof proof = (SybilResistantElectionProof) broadcastValue;
            serializeChainSeeds(proof.ChainSeeds, out);
            out.writeInt(proof.nonce);
        }

        private void serializeChainSeeds(List<Pair<UUID, byte[]>> ChainSeeds, ByteBuf out) {
            out.writeShort(ChainSeeds.size());
            for (var pair : ChainSeeds) {
                UUID id = pair.getKey();
                out.writeLong(id.getMostSignificantBits());
                out.writeLong(id.getLeastSignificantBits());
                byte[] seed = pair.getValue();
                out.writeShort(seed.length);
                out.writeBytes(seed);
            }
        }

        @Override
        public BroadcastValue deserialize(ByteBuf in) {
            List<Pair<UUID, byte[]>> ChainSeeds = deserializeChainSeeds(in);
            int nonce = in.readInt();
            return new SybilResistantElectionProof(ChainSeeds, nonce);
        }

        private List<Pair<UUID, byte[]>> deserializeChainSeeds(ByteBuf in) {
            int numChains = in.readShort();
            List<Pair<UUID, byte[]>> ChainSeeds = new ArrayList<>(numChains);
            for (int i = 0; i < numChains; i++) {
                UUID id = new UUID(in.readLong(), in.readLong());
                byte[] seed = new byte[in.readShort()];
                in.readBytes(seed);
                ChainSeeds.add(Pair.of(id, seed));
            }
            return ChainSeeds;
        }

    };

    @Override
    public ISerializer<BroadcastValue> getSerializer() {
        return serializer;
    }
}
