package applicationInterface;

public class OperationToCMuxIdentifierMapper {

    private static OperationToCMuxIdentifierMapper singleton;
    private OperationToCMuxIdentifierMapper mapper = new DefaultMapper();

private OperationToCMuxIdentifierMapper() {}

        public static OperationToCMuxIdentifierMapper getSingleton() {
        if (singleton == null)
            singleton = new OperationToCMuxIdentifierMapper();
        return singleton;
    };

    public byte[] mapToCmuxId1(byte[] operation) {
        return mapper.mapToCmuxId1(operation);
    }
    
    public byte[] mapToCmuxId2(byte[] operation) {
        return mapper.mapToCmuxId2(operation);
    }

    public void setCustomMapper(OperationToCMuxIdentifierMapper mapper) {
        this.mapper = mapper;
    }
}
