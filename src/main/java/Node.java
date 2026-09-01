import java.io.Serializable;

public class Node implements Comparable<Node>, Serializable {

    private String key;
    private String value;
    private boolean deleted;
    private Long timestamp;

    public Node(String key, String value) {
        this(key, value, false, System.currentTimeMillis());
    }

    public Node(String key, String value, Boolean deleted) {
        this(key, value, deleted, System.currentTimeMillis());
    }

    public Node(String key, String value, boolean deleted, long ts) {
        this.key = key;
        this.value = value;
        this.deleted = deleted;
        timestamp = ts;
    }

    public static Node createTombstone(String key) {
        return new Node(key, null, true);
    }


    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public int compareTo(Node node) {
        int sort = getKey().compareTo(node.getKey());
        return sort == 0 ? node.getTimestamp().compareTo(getTimestamp()) : sort;
    }
}
