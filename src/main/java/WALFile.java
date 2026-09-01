import java.io.*;
import java.util.ArrayList;

public class WALFile {

    private final String fileDir;
    private BufferedWriter writer;
    private final Object lock = new Object();

    public WALFile(String fd) throws IOException {
        fileDir = fd;
        writer = new BufferedWriter(new FileWriter(fileDir, true));
    }


    public void append(WALEntry entry) throws IOException {
        synchronized (lock) {
            writer.write(entry.toString());
            writer.newLine();
            writer.flush();
        }
    }

    public void deleteWalFile() throws IOException {
        synchronized (lock) {
            if (writer != null) {
                writer.close();
            }

            File file = new File(fileDir);
            if (file.exists()) {
                file.delete();
            }

            writer = new BufferedWriter(new FileWriter(fileDir, true));
        }
    }

    public ArrayList<WALEntry> recover() {
        ArrayList<WALEntry> walEntries = new ArrayList<>();

        File file = new File(fileDir);
        if (!file.exists()) {
            return walEntries;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            while (line != null) {
                walEntries.add(WALEntry.fromString(line));
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return walEntries;
    }

    static class WALEntry {
        private final Operation operation;
        private final String key;
        private final String value;
        private final Long timestamp;

        WALEntry(Operation op, String k, String v, Long ts) {
            operation = op;
            key = k;
            value = v;
            timestamp = ts;
        }

        public static WALEntry getPutEntry(String key, String value) {
            return new WALEntry(Operation.PUT, key, value, System.currentTimeMillis());
        }

        public static WALEntry getDeleteEntry(String key) {
            return new WALEntry(Operation.DELETE, key, null, System.currentTimeMillis());
        }

        public String toString() {
            return String.format("%s|%s|%s|%d",
                    operation, key, value != null ? value : "", timestamp);
        }

        public static WALEntry fromString(String entry) {
            if (entry == null || entry.equals("")) return null;

            String[] split = entry.split("\\|");
            if (split.length != 4) return null;

            Operation operation = Operation.valueOf(split[0]);
            String key = split[1];
            String value = split[2];
            Long ts = Long.parseLong(split[3]);

            return new WALEntry(operation, key, value, ts);
        }

        public Operation getOperation() {
            return operation;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public Long getTimestamp() {
            return timestamp;
        }
    }

    enum Operation {
        PUT, DELETE
    }
}
