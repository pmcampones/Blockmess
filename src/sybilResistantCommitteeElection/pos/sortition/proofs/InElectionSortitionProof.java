package sybilResistantCommitteeElection.pos.sortition.proofs;

import broadcastProtocols.lazyPush.exception.InnerValueIsNotBlockingBroadcast;
import io.netty.buffer.ByteBuf;
import main.CryptographicUtils;
import main.ProtoPojo;
import main.ProtoPojoAbstract;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;

public class InElectionSortitionProof extends ProtoPojoAbstract implements LargeSortitionProof {

    public static final short ID = 659;

    private final IncompleteSortitionProof proof;

    private final PublicKey proposer;

    public InElectionSortitionProof(IncompleteSortitionProof proof, PublicKey proposer) {
        super(ID);
        this.proof = proof;
        this.proposer = proposer;
    }

    public PublicKey getProposer() {
        return proposer;
    }

    public IncompleteSortitionProof getProof() {
        return proof;
    }

    @Override
    public int getRound() {
        return proof.getRound();
    }

    @Override
    public int getVotes() {
        return proof.getVotes();
    }

    @Override
    public UUID getKeyBlockId() {
        return proof.getKeyBlockId();
    }

    @Override
    public byte[] getHashProof() {
        return proof.getHashProof();
    }

    public int getSerializedSize() throws IOException {
        return proof.getSerializedSize() + CryptographicUtils.computeKeySize(proposer);
    }

    @Override
    public ISerializer<ProtoPojo> getSerializer() {
        return serializer;
    }

    public static ISerializer<ProtoPojo> serializer = new ISerializer<>() {

        @Override
        public void serialize(ProtoPojo pojo, ByteBuf out) throws IOException {
            if (!(pojo instanceof InElectionSortitionProof))
                throw new IOException(String.format(
                        "Expected to serialize a %s, but instead got a %s",
                        InElectionSortitionProof.class.getSimpleName(),
                        pojo.getClass().getSimpleName()
                ));
            InElectionSortitionProof electionProof = (InElectionSortitionProof) pojo;
            serializeInnerProof(electionProof.proof, out);
            CryptographicUtils.serializeKey(electionProof.proposer, out);
        }

        private void serializeInnerProof(IncompleteSortitionProof proof, ByteBuf out) throws IOException {
            proof.getSerializer().serialize(proof, out);
        }

        @Override
        public ProtoPojo deserialize(ByteBuf in) throws IOException {
            IncompleteSortitionProof proof = deserializeInnerProof(in);
            PublicKey proposer = CryptographicUtils.deserializePubKey(in);
            return new InElectionSortitionProof(proof, proposer);
        }

        private IncompleteSortitionProof deserializeInnerProof(ByteBuf in) throws IOException {
            ISerializer<ProtoPojo> serializer = IncompleteSortitionProof.serializer;
            ProtoPojo pojo = serializer.deserialize(in);
            if (!(pojo instanceof IncompleteSortitionProof))
                throw new IOException(String.format(
                        "Expected to deserialize a %s, but instead got a %s",
                        IncompleteSortitionProof.class.getSimpleName(),
                        pojo.getClass().getSimpleName()
                ));
            return (IncompleteSortitionProof) pojo;
        }
    };

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public UUID getBlockingID() throws InnerValueIsNotBlockingBroadcast {
        return proof.getBlockingID();
    }
}
