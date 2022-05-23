package demo.ycsb.serializers;

import demo.ycsb.DBClient;
import lombok.SneakyThrows;

import java.io.*;

public class GenericPojoSerializer {

    @SneakyThrows
    public static byte[] serializePojoCode(DBClient.OP op, Serializable obj) {
        try (var out = new ByteArrayOutputStream(); var oout = new ObjectOutputStream(out)) {
            oout.writeByte(op.ordinal());
            oout.writeObject(obj);
            oout.flush();
            out.flush();
            return out.toByteArray();
        }
    }

    @SneakyThrows
    public static byte[] serializePojo(Serializable obj) {
        try (var out = new ByteArrayOutputStream(); var oout = new ObjectOutputStream(out)) {
            oout.writeObject(obj);
            oout.flush();
            out.flush();
            return out.toByteArray();
        }
    }


    @SneakyThrows
    public static <E extends Serializable> E deserialize(ObjectInputStream in) throws IOException {
        return (E) in.readObject();
    }

    @SneakyThrows
    public static <E extends Serializable> E deserialize(byte[] content) throws IOException{
        try(var in = new ByteArrayInputStream(content); var oin = new ObjectInputStream(in)) {
            return (E) oin.readObject();
        }
    }

}
