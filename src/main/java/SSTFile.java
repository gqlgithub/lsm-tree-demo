import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SSTFile implements Comparable<SSTFile> {
    private String firstKey;
    private final String path;

    public SSTFile(String path) {
        this.path = path;
    }

    public void write(Collection<Node> nodes) throws IOException {
        try (DataOutputStream dataOutputStream = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(path)))) {
            Node first = (Node) nodes.toArray()[0];
            firstKey = first.getKey();
            dataOutputStream.writeUTF(firstKey);
            dataOutputStream.writeLong(nodes.size());

            for (Node node : nodes) {
                dataOutputStream.writeUTF(node.getKey());
                dataOutputStream.writeBoolean(node.isDeleted());
                if (!node.isDeleted()) {
                    dataOutputStream.writeUTF(node.getValue());
                }
                dataOutputStream.writeLong(node.getTimestamp());
            }
        }
    }

    public Node read(String key) {
        try (DataInputStream dataInputStream
                     = new DataInputStream(new BufferedInputStream(new FileInputStream(path)))) {

            String firstKey = dataInputStream.readUTF();
            long total = dataInputStream.readLong();

            while (total-- > 0) {
                String k = dataInputStream.readUTF();
                if (k.compareTo(key) > 0) break;
                String value = null;
                boolean deleted = dataInputStream.readBoolean();
                if (!deleted) {
                    value = dataInputStream.readUTF();
                }
                long ts = dataInputStream.readLong();
                if (key.equals(k)) {
                    return new Node(key, value, false, ts);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Node> getAllEntries() {
        ArrayList<Node> nodes = new ArrayList<>();
        try (DataInputStream dataInputStream
                     = new DataInputStream(new BufferedInputStream(new FileInputStream(path)))) {

            String firstKey = dataInputStream.readUTF();
            long total = dataInputStream.readLong();

            while (total-- > 0) {
                String key = dataInputStream.readUTF();
                String value = null;
                boolean deleted = dataInputStream.readBoolean();
                if (!deleted) {
                    value = dataInputStream.readUTF();
                }
                long ts = dataInputStream.readLong();
                if (value != null) {
                    nodes.add(new Node(key, value, false, ts));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return nodes;
    }

    public void delete() throws IOException {
        Files.deleteIfExists(Paths.get(path));
    }

    public String getFirstKey() {
        return firstKey;
    }

    public void setFirstKey(String firstKey) {
        this.firstKey = firstKey;
    }

    public String getPath() {
        return path;
    }

    @Override
    public int compareTo(SSTFile o) {
        if (firstKey != null && o.getFirstKey() != null) {
            return firstKey.compareTo(o.firstKey);
        }
        return path.compareTo(o.path);
    }
}
