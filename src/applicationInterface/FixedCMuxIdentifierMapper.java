package applicationInterface;

public class FixedCMuxIdentifierMapper implements OperationToCMuxIdentifierMapper {

    private static FixedCMuxIdentifierMapper singleton;
    private OperationToCMuxIdentifierMapper mapper = new DefaultMapper();

private FixedCMuxIdentifierMapper() {}

        public static FixedCMuxIdentifierMapper getSingleton() {
        if (singleton == null)
            singleton = new FixedCMuxIdentifierMapper();
        return singleton;
    };

    @Override
    public byte[] mapToCmuxId1(byte[] operation) {
        return mapper.mapToCmuxId1(operation);
    }

    @Override
    public byte[] mapToCmuxId2(byte[] operation) {
        return mapper.mapToCmuxId2(operation);
    }

    public void setCustomMapper(OperationToCMuxIdentifierMapper mapper) {
        this.mapper = mapper;
    }
}
