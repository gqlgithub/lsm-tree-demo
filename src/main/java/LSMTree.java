import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class LSMTree {
    private final long memSize;
    private MemTable memTable;
    private final Queue<MemTable> muteMemTables;
    private final WALFile walFile;
    private final ConcurrentSkipListSet<SSTFile> sstFiles;

    private Compaction compaction;
    private final ExecutorService compactionExecutor;

    private final String dir;
    private final ReadWriteLock lock;

    public LSMTree(long size, String dir) throws IOException {
        this.dir = dir;

        memSize = size;
        memTable = new MemTable(size);
        muteMemTables = new LinkedList<>();
        walFile = new WALFile(dir + "/wal/wal.log");
        sstFiles = new ConcurrentSkipListSet<>();

        compactionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "compaction thread");
            thread.setDaemon(true);
            return thread;
        });

        lock = new ReentrantReadWriteLock();
        recover();
        startCompactionTask();
    }

    public void put(String key, String value) throws IOException {
        if (key == null || value == null) return;

        lock.writeLock().lock();
        try {
            walFile.append(WALFile.WALEntry.getPutEntry(key, value));

            memTable.put(key, value);
            if (memTable.shouldFlush()) {

                muteMemTables.add(memTable);
                memTable = new MemTable(memSize);
                flushToFile(muteMemTables);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            lock.writeLock().unlock();
        }
    }

    private void flushToFile(Queue<MemTable> muteMemTables) throws IOException {
        MemTable memTable = muteMemTables.poll();
        assert memTable != null;

        String path = String.format("%s/sst/sst_level0_%d.db", dir, System.currentTimeMillis());
        SSTFile sstFile = new SSTFile(path);
        sstFile.write(memTable.getAllEntries());

        sstFiles.add(sstFile);
        walFile.deleteWalFile();
        compaction();
    }

    public String get(String key) {
        if (key == null) return null;

        String value = null;
        lock.readLock().lock();
        try {
            value = memTable.get(key);
            if (value != null) return value;
            for (MemTable memTable : muteMemTables) {
                value = memTable.get(key);
                if (value != null) return value;
            }

            value = getFromSSTFile(key);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.readLock().unlock();
        }

        return value;
    }

    private String getFromSSTFile(String key) {
//        SSTFile tmp = new SSTFile("tmp");
//        tmp.setFirstKey(key);
//        Set<SSTFile> sstFiles = this.sstFiles.headSet(tmp, true);

        Node node = null;
        for (SSTFile sstFile : sstFiles) {

            Node cur = sstFile.read(key);
            if (cur != null && !cur.isDeleted()) {
                if (node == null) {
                    node = cur;
                } else {
                    node = cur.getTimestamp() > node.getTimestamp() ? cur : node;
                }
            }
        }
        return node != null ? node.getValue() : null;
    }

    public void delete(String key) throws IOException {
        if (key == null) return;

        lock.writeLock().lock();
        try {
            walFile.append(WALFile.WALEntry.getDeleteEntry(key));
            memTable.remove(key);
            if (memTable.shouldFlush()) {
                if (memTable.shouldFlush()) {
                    muteMemTables.add(memTable);
                    memTable = new MemTable(memSize);
                    flushToFile(muteMemTables);
                }
            }
        } catch (Exception e) {
            lock.writeLock().unlock();
        }
    }

    public void recover() {
        File fileDir = new File(dir + "\\sst");
        File[] files = fileDir.listFiles();
        if (files != null) {

            for (File sst : files) {

                try (DataInputStream dataInputStream =
                             new DataInputStream(new BufferedInputStream(new FileInputStream(sst)))) {
                    String firstKey = dataInputStream.readUTF();
                    SSTFile sstFile = new SSTFile(sst.getPath());
                    sstFile.setFirstKey(firstKey);
                    sstFiles.add(sstFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        ArrayList<WALFile.WALEntry> walEntries = walFile.recover();
        for (WALFile.WALEntry entry : walEntries) {
            if (WALFile.Operation.PUT == entry.getOperation()) {
                memTable.put(entry.getKey(), entry.getValue());
            } else {
                memTable.remove(entry.getKey());
            }
        }
    }

    public void startCompactionTask() {
        compactionExecutor.submit(()->{
            while (!Thread.currentThread().isInterrupted()){
                try{
                    Thread.sleep(1000);
                    compaction();
                }catch (InterruptedException exception){
                    Thread.currentThread().interrupt();
                    break;
                }catch (Exception exception){
                    exception.printStackTrace();
                }
            }
        });
    }

    public void compaction() throws IOException {
        lock.writeLock().lock();
        try{
            Compaction compactionTask = new Compaction(4, String.format("%s/sst", dir), sstFiles);

            compactionTask.compaction(0);
            ConcurrentHashMap<Integer, List<String>> levelFilesMap = compactionTask.getLevelFilesMap();
            Collection<List<String>> values = levelFilesMap.values();

            sstFiles.clear();
            for (List<String> list : values){
                List<SSTFile> collect = list.stream().map(SSTFile::new).collect(Collectors.toList());
                sstFiles.addAll(collect);
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            lock.writeLock().unlock();
        }
    }
}
