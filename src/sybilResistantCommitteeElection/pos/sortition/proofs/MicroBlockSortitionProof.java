package sybilResistantCommitteeElection.pos.sortition.proofs;

import io.netty.buffer.ByteBuf;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.UUID;

/**
 * The sybil election proof for a microblock.
 * <p>Effectively, the proof could be empty.
 * The only requirement existent for a microblock to be valid is
 * having the same proposer as the corresponding keyblock.
 * If all microblocks in a chain are valid,
 * then this rule can be further simplified and we can state that a microblock is valid
 * if it has the same proposer as the previous microblock.</p>
 * <p>The required verification would need access to the identifier of the previous block,
 * which can be attained in the block itself. However, without that information,
 * the proof cannot be computed. So it is repeated in this object.</p>
 * <p>The associatedKeyBlock field is not necessary at all to verify the MicroBlockSortitionProof,
 * however, it is necessary to validate subsequent KeyBlocks.</p>
 */
public class MicroBlockSortitionProof extends ProtoPojoAbstract implements SortitionProof, KeyBlockReferencing {

    public static final short ID = 6378;

    //private final UUID associatedKeyBlock;

    /**
     * I know it does not make sense to have these two variables instead of the associatedKeyBlock above,
     * but the program only works with them.
     * <p>Without these variables, for some unknown reason, the serialization of the enveloping block will differ
     * between processes, despite the value in associatedKeyBlock remaining the same.</p>
     * <p>This is some voodoo solution, but it works. Alter at your own risk</p>
     */
    private final long mostSignificantBits;

    private final long leastSignificantBits;

    public MicroBlockSortitionProof(UUID associatedKeyBlock) {
        super(ID);
        //this.associatedKeyBlock = associatedKeyBlock;
        mostSignificantBits = associatedKeyBlock.getMostSignificantBits();
        leastSignificantBits = associatedKeyBlock.getLeastSignificantBits();
    }

    public UUID getAssociatedKeyBlock() {
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    @Override
    public int getSerializedSize() throws IOException {
        return 2 * Long.BYTES;
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static final ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo pojo, ByteBuf out) {
            MicroBlockSortitionProof proof = (MicroBlockSortitionProof) pojo;
            out.writeLong(proof.mostSignificantBits);
            out.writeLong(proof.leastSignificantBits);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) {
            UUID associatedKeyBlock = new UUID(in.readLong(), in.readLong());
            return new MicroBlockSortitionProof(associatedKeyBlock);
        }
    };

    @Override
    public String toString() {
        return "Microblock referencing: " + new UUID(mostSignificantBits, leastSignificantBits);
    }
}
