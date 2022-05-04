package catecoin.posSpecific.keyBlockManagers;

public class NoHeaviestKeyBlockException extends Exception {

    public static final String DEFAULT_MESSAGE =
            "Unable to identify one heaviest key block." +
                    "This probably happened because somehow the KeyBlockManager had no blocks recorded.";

    public NoHeaviestKeyBlockException() {
        super(DEFAULT_MESSAGE);
    }
}
