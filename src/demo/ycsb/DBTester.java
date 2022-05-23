package demo.ycsb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import site.ycsb.ByteArrayByteIterator;
import site.ycsb.ByteIterator;
import site.ycsb.Status;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DBTester {

    private final DBClient db = new DBClient();

    @BeforeAll
    static void beforeAll() {
        new DBClient().reset();
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
        Map<String, ByteIterator> fields = IntStream.range(0,10).boxed().collect(Collectors.toMap(i -> "f" + i,
                i -> new ByteArrayByteIterator(new byte[]{i.byteValue()})));
        db.insert("users", "pablo", fields);
        Map<String, ByteIterator> extracted = new HashMap<>(10);
        var status = db.read("users", "pablo", new HashSet<>(), extracted);
        assertEquals(Status.OK, status);
        assertTrue(IntStream.range(0, 10).mapToObj(i -> "f" + i).allMatch(extracted::containsKey));
    }

    @Test
    void shouldReadAllFieldsExplicit() {
        Map<String, ByteIterator> fields = IntStream.range(0,10).boxed().collect(Collectors.toMap(i -> "f" + i,
                i -> new ByteArrayByteIterator(new byte[]{i.byteValue()})));
        db.insert("users", "pablo", fields);
        Map<String, ByteIterator> extracted = new HashMap<>(10);
        var status = db.read("users", "pablo", fields.keySet(), extracted);
        assertEquals(Status.OK, status);
        assertTrue(IntStream.range(0, 10).mapToObj(i -> "f" + i).allMatch(extracted::containsKey));
    }

    @Test
    void shouldReadSomeFields() {
        Map<String, ByteIterator> fields = IntStream.range(0,10).boxed().collect(Collectors.toMap(i -> "f" + i,
                i -> new ByteArrayByteIterator(new byte[]{i.byteValue()})));
        db.insert("users", "pablo", fields);
        Set<String> queriedFields = IntStream.range(0, 10).filter(i -> i % 2 == 0)
                .mapToObj(i -> "f" + i).collect(toSet());
        Map<String, ByteIterator> extracted = new HashMap<>(5);
        var status = db.read("users", "pablo", queriedFields, extracted);
        assertEquals(Status.OK, status);
        assertTrue(IntStream.range(0, 10).filter(i -> i % 2 == 0)
                .mapToObj(i -> "f" + i).allMatch(extracted::containsKey));
        assertTrue(IntStream.range(0, 10).filter(i -> i % 2 == 1)
                .mapToObj(i -> "f" + i).noneMatch(extracted::containsKey));
    }

    @Test
    void shouldScanFullExtent() {
        IntStream.range(0, 10).mapToObj(String::valueOf).forEach(key -> db.insert("users", key, new HashMap<>()));
        Vector<HashMap<String, ByteIterator>> extracted = new Vector<>();
        var status = db.scan("users", "1", 5, new HashSet<>(), extracted);
        assertEquals(Status.OK, status);
        assertEquals(5, extracted.size());
    }

    @Test
    void shouldScanUntilEnd() {
        IntStream.range(0, 10).mapToObj(String::valueOf).forEach(key -> db.insert("users", key, new HashMap<>()));
        Vector<HashMap<String, ByteIterator>> extracted = new Vector<>();
        var status = db.scan("users", "6", 5, new HashSet<>(), extracted);
        assertEquals(Status.OK, status);
        assertEquals(4, extracted.size());
    }

}
