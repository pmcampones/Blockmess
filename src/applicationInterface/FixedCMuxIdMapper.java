package applicationInterface;

public class FixedCMuxIdMapper implements CMuxIdMapper {

    private static FixedCMuxIdMapper singleton;
    private CMuxIdMapper mapper = new DefaultMapper();

private FixedCMuxIdMapper() {}

        public static FixedCMuxIdMapper getSingleton() {
        if (singleton == null)
            singleton = new FixedCMuxIdMapper();
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

    public void setCustomMapper(CMuxIdMapper mapper) {
        this.mapper = mapper;
    }
}
