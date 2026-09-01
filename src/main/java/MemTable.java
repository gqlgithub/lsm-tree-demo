import java.util.Collection;
import java.util.concurrent.ConcurrentSkipListMap;

public class MemTable {

    private final long maxSize;

    private final ConcurrentSkipListMap<String, Node> skipList;

    public MemTable(long maxSize) {
        this.maxSize = maxSize;
        skipList = new ConcurrentSkipListMap<>();
    }

    public void put(String key, String value) {
        Node node = new Node(key, value);
        skipList.put(key, node);
    }

    public boolean shouldFlush() {
        return skipList.size() > maxSize;
    }

    public Collection<Node> getAllEntries() {
        return skipList.values();
    }

    public String get(String key) {
        Node node = skipList.get(key);
        return node != null ? node.getValue() : null;
    }

    public void remove(String key) {
        skipList.remove(key);
        Node tombstone = Node.createTombstone(key);
        skipList.put(key, tombstone);
    }
}
