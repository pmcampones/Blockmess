package utils;

import java.util.concurrent.atomic.AtomicInteger;

public class IDGenerator {

    private static final AtomicInteger current = new AtomicInteger(100);
    //some of the first vals may be occupied

    public static short genId() {
        return (short) current.incrementAndGet();
    }
}
