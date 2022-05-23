package demo.ycsb;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class Table {

    private final SortedMap<String, Map<String, byte[]>> records = new TreeMap<>();

    public Optional<Map<String, byte[]>> readRecord(String key, Set<String> fields) {
        var record = records.get(key);
        if (record == null)
            return Optional.empty();
        return Optional.of(fields.isEmpty() ? record : filterUnusedFields(fields, record));
    }

    private Map<String, byte[]> filterUnusedFields(Set<String> fields, Map<String, byte[]> record) {
        return record.entrySet().stream().filter(e -> fields.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Optional<Map<String, byte[]>> insertRecord(String key, Map<String, byte[]> value) {
        var oldRecord = records.put(key, value);
        return oldRecord == null ? Optional.empty() : Optional.of(oldRecord);
    }

    public Optional<Map<String, byte[]>> updateRecord(String key, Map<String, byte[]> fields) {
        var record = records.get(key);
        if (record == null)
            return Optional.empty();
        record.putAll(fields);
        return Optional.of(record);
    }

    public Optional<Map<String, byte[]>> deleteRecord(String key) {
        var removed = records.remove(key);
        return removed == null ? Optional.empty() : Optional.of(removed);
    }

    public List<Map<String, byte[]>> scanRecords(String startKey, int recordCount, Set<String> fields) {
        List<Map<String, byte[]>> scannedRecords = getRecords(startKey, recordCount);
        return fields.isEmpty() ? scannedRecords : filterUnusedFields(fields, scannedRecords);
    }

    @NotNull
    private List<Map<String, byte[]>> filterUnusedFields(Set<String> fields, Collection<Map<String, byte[]>> scannedRecords) {
        return scannedRecords.stream().map(e -> filterUnusedFields(fields, e)).collect(Collectors.toList());
    }

    @NotNull
    private List<Map<String, byte[]>> getRecords(String startKey, int recordCount) {
        var subRecords = records.tailMap(startKey);
        var it = subRecords.values().iterator();
        var res = new Vector<Map<String, byte[]>>(Math.min(recordCount, subRecords.size()));
        for (int i = 0; i < recordCount && it.hasNext(); i++)
            res.add(it.next());
        return res;
    }

}
