package demo.ycsb.serializers;

import demo.ycsb.DBClient;
import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class GenericPojoSerializer {

    @SneakyThrows
    public static byte[] serializePojoRequest(DBClient.OP op, Serializable obj) {
        try (var out = new ByteArrayOutputStream(); var oout = new ObjectOutputStream(out)) {
            oout.writeByte(op.ordinal());
            oout.writeObject(obj);
            oout.flush();
            out.flush();
            return out.toByteArray();
        }
    }

    @SneakyThrows
    public static byte[] serializePojoResponse(DBClient.RETURN_CODES op, Serializable obj) {
        try (var out = new ByteArrayOutputStream(); var oout = new ObjectOutputStream(out)) {
            oout.writeByte(op.ordinal());
            oout.writeObject(obj);
            oout.flush();
            out.flush();
            return out.toByteArray();
        }
    }


    @SneakyThrows
    public static <E extends Serializable> E deserialize(ObjectInputStream in) {
        return (E) in.readObject();
    }

}
