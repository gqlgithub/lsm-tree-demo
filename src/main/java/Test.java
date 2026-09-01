import java.io.IOException;

public class Test {

    public static void main(String[] args) throws IOException {
        String dir = "lsm_tmp";
        LSMTree lsmTree = new LSMTree(1000, dir);

        System.out.println("========    function test start    ===========");

        // put
        lsmTree.put("key1", "value1");
        lsmTree.put("key2", "value2");
        lsmTree.put("key3", "value3");

        // get
        System.out.println(lsmTree.get("key1"));
        System.out.println(lsmTree.get("key2"));
        System.out.println(lsmTree.get("key3"));

        // update
        lsmTree.put("key1", "value1value1");
        System.out.println(lsmTree.get("key1"));

        // delete
        lsmTree.delete("key2");

        int testCount = 10000;
        // compaction
        for (int i = 0; i < testCount; i++) {
            lsmTree.put("k" + i, "v" + i);
        }
        for (int i = 0; i < testCount; i++) {
            String key = "k" + (int) (testCount * Math.random());
            String value = lsmTree.get(key);
            if (value == null) {
                System.out.println(key + " is null");
                break;
            }
        }

        System.out.println("========    function test end   ===========");
    }
}
