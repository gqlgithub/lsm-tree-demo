import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class Compaction {
    private final ConcurrentHashMap<Integer, List<String>> levelFilesMap;
    private final int levelMaxFileCount;
    private final String sstDir;

    Compaction(int maxFile, String dir, ConcurrentSkipListSet<SSTFile> files) {
        levelMaxFileCount = maxFile;
        sstDir = dir;
        levelFilesMap = groupByLevel(files);
    }

    public boolean isNeedCompaction(int level) {
        return levelFilesMap != null
                && levelFilesMap.get(level) != null
                && levelFilesMap.get(level).size() >= levelMaxFileCount;
    }

    public void compaction(int level) throws IOException {
        if (!isNeedCompaction(level)) return;

        List<String> sstFiles = levelFilesMap.get(level);

        // do compaction
        String sstFileName = doCompaction(level, sstFiles);

        // add sst file to level-map
        levelFilesMap.computeIfAbsent(level + 1, ArrayList::new).add(sstFileName);

        // clean pre sst disk memory
        for (String sst : sstFiles) {
            SSTFile file = new SSTFile(sst);
            file.delete();
        }
        levelFilesMap.get(level).clear();

        compaction(level + 1);
    }

    public String doCompaction(int currentLevel, List<String> sstFileNameList) throws IOException {
        ArrayList<SSTFile> sstFiles = new ArrayList<>();
        for (String sstName : sstFileNameList) {
            sstFiles.add(new SSTFile(sstName));
        }

        // merge
        List<Node> mergedNodes = mergeSSTFile(sstFiles);

        // remove
        List<Node> cleanNodes = doRemove(mergedNodes);

        // create sst
        String sstFileName = createSSTFileName(currentLevel + 1);
        SSTFile sstFile = new SSTFile(sstFileName);
        sstFile.write(cleanNodes);

        return sstFileName;
    }

    public List<Node> mergeSSTFile(List<SSTFile> sstFileList) {
        PriorityQueue<SSTFileIterator> priorityQueue = new PriorityQueue<>();
        List<Node> nodes = new LinkedList<>();

        for (SSTFile file : sstFileList) {
            priorityQueue.add(new SSTFileIterator(file));
        }
        while (!priorityQueue.isEmpty()) {
            SSTFileIterator iterator = priorityQueue.poll();
            nodes.add(iterator.next());
            if (iterator.hasNext()) {
                priorityQueue.add(iterator);
            }
        }

        return nodes;
    }

    public List<Node> doRemove(List<Node> nodes) {
        ArrayList<Node> cleanNodes = new ArrayList<>();

        Node node = nodes.get(0);
        String key = node.getKey();
        if (!node.isDeleted()) {
            cleanNodes.add(node);
        }
        for (int i = 1; i < nodes.size(); i++) {
            Node cur = nodes.get(i);
            if (!cur.getKey().equals(key) && !cur.isDeleted()) {
                cleanNodes.add(cur);
            }
        }

        return cleanNodes;
    }


    private ConcurrentHashMap<Integer, List<String>> groupByLevel(ConcurrentSkipListSet<SSTFile> files) {
        ConcurrentHashMap<Integer, List<String>> levelMap = new ConcurrentHashMap<>();
        if (files == null) return levelMap;

        while (!files.isEmpty()) {
            SSTFile sstFile = files.pollFirst();
            String path = sstFile.getPath();
            int level = path.charAt(path.indexOf("level") + 5) - '0';
            levelMap.computeIfAbsent(level, ArrayList::new).add(path);
        }

        return levelMap;
    }

    public String createSSTFileName(int level) {
        return String.format("%s/sst_level%d_%d.db", sstDir, level, System.currentTimeMillis());
    }

    public ConcurrentHashMap<Integer, List<String>> getLevelFilesMap() {
        return levelFilesMap;
    }

    static class SSTFileIterator implements Comparable<SSTFileIterator> {
        private int index;
        private final List<Node> nodes;

        SSTFileIterator(SSTFile sstFile) {
            nodes = sstFile.getAllEntries();
        }

        public boolean hasNext() {
            return index < nodes.size();
        }

        public Node next() {
            return nodes.get(index++);
        }

        public Node get() {
            return nodes.get(index);
        }

        public int compareTo(SSTFileIterator iterator) {
            int sort = get().getKey().compareTo(iterator.get().getKey());
            return sort == 0 ? get().getTimestamp().compareTo(iterator.get().getTimestamp()) : sort;
        }
    }
}
