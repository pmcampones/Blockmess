package demo.ycsb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import site.ycsb.ByteArrayByteIterator;
import site.ycsb.ByteIterator;
import site.ycsb.Status;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class DBTester {

    private final DBClient db = DBClient.getSingleton();

    @BeforeAll
    static void beforeAll() {
        DBClient.getSingleton().reset();
    }

    @AfterEach
    void afterEach() {
        db.reset();
    }

    @Test
    void shouldNotFindTableInRead() {
        var status = db.read("users", "pablo", new HashSet<>(), new HashMap<>());
        assertEquals(Status.NOT_FOUND, status);
    }

    @Test
    void shouldNotFindTableInScan() {
        var status = db.scan("users", "pablo", 10, new HashSet<>(), new Vector<>());
        assertEquals(Status.NOT_FOUND, status);
    }

    @Test
    void shouldNotFindTableInUpdate() {
        var status = db.update("users", "pablo", new HashMap<>());
        assertEquals(Status.NOT_FOUND, status);
    }

    @Test
    void shouldNotFindTableInDelete() {
        var status = db.delete("users", "pablo");
        assertEquals(Status.NOT_FOUND, status);

    }

    @Test
    void shouldInsertInNonExistentTable() {
        var status = db.insert("users", "pablo", new HashMap<>());
        assertEquals(Status.OK, status);
    }

    @Test
    void shouldNotFindUserInRead() {
        db.insert("users", "not_pablo", new HashMap<>());
        var status = db.read("users", "pablo", new HashSet<>(), new HashMap<>());
        assertEquals(Status.NOT_FOUND, status);
    }

    @Test
    void shouldNotFindUserInScan() {
        db.insert("users", "not_pablo", new HashMap<>());
        var status = db.scan("users", "pablo", 10, new HashSet<>(), new Vector<>());
        assertEquals(Status.NOT_FOUND, status);
    }

    @Test
    void shouldNotFindUserInUpdate() {
        db.insert("users", "not_pablo", new HashMap<>());
        var status = db.update("users", "pablo", new HashMap<>());
        assertEquals(Status.NOT_FOUND, status);
    }

    @Test
    void shouldNotFindUserInDelete() {
        db.insert("users", "not_pablo", new HashMap<>());
        var status = db.delete("users", "pablo");
        assertEquals(Status.NOT_FOUND, status);
    }

    @Test
    void shouldInsertInExistentTable() {
        db.insert("users", "a", new HashMap<>());
        var status = db.insert("users", "b", new HashMap<>());
        assertEquals(Status.OK, status);
    }

    @Test
    void shouldInsertOverExistentUser() {
        db.insert("users", "pablo", new HashMap<>());
        var status = db.insert("users", "pablo", new HashMap<>());
        assertEquals(Status.OK, status);
    }

    @Test
    void shouldInsertWithFields() {
        Map<String, ByteIterator> fields = new HashMap<>();
        fields.put("f1", new ByteArrayByteIterator(new byte[]{1,2,3}));
        var status = db.insert("users", "pablo", fields);
        assertEquals(Status.OK, status);
    }

    @Test
    void shouldInsertExistentTableWithFields() {
        db.insert("users", "pablo", new HashMap<>());
        Map<String, ByteIterator> fields = new HashMap<>();
        fields.put("f1", new ByteArrayByteIterator(new byte[]{1,2,3}));
        var status = db.insert("users", "pablo", fields);
        assertEquals(Status.OK, status);
    }

    @Test
    void shouldReadExistentUser() {
        db.insert("users", "pablo", new HashMap<>());
        var status = db.read("users", "pablo", new HashSet<>(), new HashMap<>());
        assertEquals(Status.OK, status);
    }

    @Test
    void shouldScanExistentUsers() {
        db.insert("users", "a", new HashMap<>());
        db.insert("users", "c", new HashMap<>());
        Vector<HashMap<String, ByteIterator>> output = new Vector<>();
        var status = db.scan("users", "a", 2, new HashSet<>(), output);
        assertEquals(Status.OK, status);
        System.out.println(output);
    }

    @Test
    void shouldUpdateExistentUser() {
        db.insert("users", "pablo", new HashMap<>());
        Map<String, ByteIterator> fields = new HashMap<>();
        fields.put("f1", new ByteArrayByteIterator(new byte[]{1,2,3}));
        var status = db.update("users", "pablo", fields);
        assertEquals(Status.OK, status);
    }

    @Test
    void shouldReadAllFieldsImplicit() {
        Map<String, ByteIterator> fields = new HashMap<>(10);
        for (int i = 0; i < 10; i++)
            fields.put("f" + i, new ByteArrayByteIterator(new byte[]{(byte) i}));
        db.insert("users", "pablo", fields);
        Map<String, ByteIterator> extracted = new HashMap<>(10);
        var status = db.read("users", "pablo", new HashSet<>(), extracted);
        assertEquals(Status.OK, status);
        for (int i = 0; i < 10; i++)
            assertTrue(extracted.containsKey("f" + i));
    }

    @Test
    void shouldReadAllFieldsExplicit() {
        Map<String, ByteIterator> fields = new HashMap<>(10);
        for (int i = 0; i < 10; i++)
            fields.put("f" + i, new ByteArrayByteIterator(new byte[]{(byte) i}));
        db.insert("users", "pablo", fields);
        Map<String, ByteIterator> extracted = new HashMap<>(10);
        var status = db.read("users", "pablo", fields.keySet(), extracted);
        assertEquals(Status.OK, status);
        for (int i = 0; i < 10; i++)
            assertTrue(extracted.containsKey("f" + i));
    }

    @Test
    void shouldReadSomeFields() {
        Map<String, ByteIterator> fields = new HashMap<>(10);
        for (int i = 0; i < 10; i++)
            fields.put("f" + i, new ByteArrayByteIterator(new byte[]{(byte) i}));
        db.insert("users", "pablo", fields);
        Set<String> queriedFields = new HashSet<>(5);
        for (int i = 0; i < 10; i+= 2)
            queriedFields.add("f" + i);
        Map<String, ByteIterator> extracted = new HashMap<>(5);
        var status = db.read("users", "pablo", queriedFields, extracted);
        assertEquals(Status.OK, status);
        for (int i = 0; i < 10; i += 2) assertTrue(extracted.containsKey("f" + i));
        for (int i = 1; i < 10; i += 2) assertFalse(extracted.containsKey("f" + i));
    }

}
