
package edu.ucla.library.avpairtree.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Constants used in testing.
 */
public final class TestConstants {

    /**
     * A default host address for testing.
     */
    public static final String LOCALHOST = "localhost";

    /**
     * A watch directory for testing.
     */
    public static final String WATCHED_DIR = "src/test/resources/csvs/watched/";

    /**
     * The location of CSV test fixtures.
     */
    public static final String CSV_DIR = "src/test/resources/csvs/";

    /**
     * A sample video CSV.
     */
    public static final String SYNANON = "video_synanon_2021_03_22.csv";

    /**
     * A sample audio CSV.
     */
    public static final String SOUL = "soul_collection_2021_03_22.csv";

    /*
     * A smaller sample audio CSV
     */
    public static final String SOUL_SAMPLE = "job_map_test.csv";

    /**
     * The file name property from the JSON message format.
     */
    public static final String FILE_PATH = "FilePath";

    /**
     * The item ARK property from the JSON message format.
     */
    public static final String ITEM_ARK = "ItemARK";

    /**
     * The item's path root property from the JSON message format.
     */
    public static final String PATH_ROOT = "PathRoot";

    /**
     * The item's processing status property from the JSON message format.
     */
    public static final String PROCESSED = "Processed";

    /**
     * The item's waveform property from the JSON message format.
     */
    public static final String WAVEFORM = "Waveform";

    /**
     * The ARKs from the test fixtures.
     */
    public static final Set<String> EXPECTED_ARKS = new HashSet<>(Arrays.asList("ark:/21198/zz002dvwr6",
            "ark:/21198/zz002dvx4c", "ark:/21198/zz002dvxgj", "ark:/21198/zz002dvxmm", "ark:/21198/zz002dvxn4",
            "ark:/21198/zz002dvxpn", "ark:/21198/zz002dvxq5", "ark:/21198/zz002dvxtq", "ark:/21198/zz002dvz19",
            "ark:/21198/zz002dvz3b", "ark:/21198/zz002dvz5c", "ark:/21198/zz002dvz6w", "ark:/21198/zz002dvz7d",
            "ark:/21198/zz002dvzcg", "ark:/21198/zz002dvzfh", "ark:/21198/zz002dvzhj", "ark:/21198/zz002dvzkk",
            "ark:/21198/zz002dvzqn", "ark:/21198/zz002dvzw7", "ark:/21198/zz002dw017", "ark:/21198/zz002dw02r",
            "ark:/21198/zz002dw0gz", "ark:/21198/zz002dw0j0", "ark:/21198/zz002dw0qk", "ark:/21198/zz002dw0r3",
            "ark:/21198/zz002dw0t4", "ark:/21198/zz002dw0xp", "ark:/21198/zz002dw148", "ark:/21198/zz002dw15s",
            "ark:/21198/zz002dvwsq/8345jp78", "ark:/21198/zz002dvwsq/c930vc6r", "ark:/21198/zz002dvwt7/7s64061s",
            "ark:/21198/zz002dvwt7/bd50r31z", "ark:/21198/zz002dvwvr/0480d10t", "ark:/21198/zz002dvwvr/bw32g32s",
            "ark:/21198/zz002dvww8/df44r11x", "ark:/21198/zz002dvww8/xd87qx8f", "ark:/21198/zz002dvwxs/db61095f",
            "ark:/21198/zz002dvwxs/np23454h", "ark:/21198/zz002dvwz9/9f10zz4n", "ark:/21198/zz002dvwz9/gh94nf5s",
            "ark:/21198/zz002dvx09/31749h3q", "ark:/21198/zz002dvx09/zs37dw9w", "ark:/21198/zz002dvx1t/6v266346",
            "ark:/21198/zz002dvx1t/9c81kr10", "ark:/21198/zz002dvx2b/9h69k78b", "ark:/21198/zz002dvx2b/q683595g",
            "ark:/21198/zz002dvx3v/hq597s7v", "ark:/21198/zz002dvx3v/zf41rr0n", "ark:/21198/zz002dvx5w/2f13382n",
            "ark:/21198/zz002dvx5w/4822jw8g", "ark:/21198/zz002dvx6d/7868wd4f", "ark:/21198/zz002dvx6d/zm478d2q",
            "ark:/21198/zz002dvx7x/kj30zd61", "ark:/21198/zz002dvx7x/x0623j2c", "ark:/21198/zz002dvx8f/4p821p7q",
            "ark:/21198/zz002dvx8f/km97n41n", "ark:/21198/zz002dvx9z/6g23dv57", "ark:/21198/zz002dvx9z/6z30r56s",
            "ark:/21198/zz002dvxbg/4994mm34", "ark:/21198/zz002dvxbg/q4253q4r", "ark:/21198/zz002dvxc0/8v21sc09",
            "ark:/21198/zz002dvxc0/r567jh86", "ark:/21198/zz002dvxdh/0s11nh07", "ark:/21198/zz002dvxdh/d863d81m",
            "ark:/21198/zz002dvxf1/3s46dm6k", "ark:/21198/zz002dvxf1/k6345n40", "ark:/21198/zz002dvxh2/0n391g1q",
            "ark:/21198/zz002dvxh2/8p508b2d", "ark:/21198/zz002dvxjk/fd147p02", "ark:/21198/zz002dvxjk/kx40xk6q",
            "ark:/21198/zz002dvxk3/1v38h149", "ark:/21198/zz002dvxk3/sn72tv4p", "ark:/21198/zz002dvxrp/c510kv16",
            "ark:/21198/zz002dvxrp/sv833c7v", "ark:/21198/zz002dvxs6/0t81n32f", "ark:/21198/zz002dvxs6/xz099k0q",
            "ark:/21198/zz002dvxv7/vz354d42", "ark:/21198/zz002dvxv7/z376f46s", "ark:/21198/zz002dvxwr/ch16378d",
            "ark:/21198/zz002dvxwr/ch90kh6z", "ark:/21198/zz002dvxx8/5z323r78", "ark:/21198/zz002dvxx8/s196h264",
            "ark:/21198/zz002dvxzs/h205v76h", "ark:/21198/zz002dvxzs/n2437r3h", "ark:/21198/zz002dvz0s/0248p985",
            "ark:/21198/zz002dvz0s/0h26rq4w", "ark:/21198/zz002dvz2t/1h60js07", "ark:/21198/zz002dvz2t/2q57ss3r",
            "ark:/21198/zz002dvz4v/b502bd3c", "ark:/21198/zz002dvz4v/jg380m2z", "ark:/21198/zz002dvz8x/ps83n43v",
            "ark:/21198/zz002dvz8x/qq793h91", "ark:/21198/zz002dvz9f/kd06hr89", "ark:/21198/zz002dvz9f/wv08bp2s",
            "ark:/21198/zz002dvzbz/6d43hc7r", "ark:/21198/zz002dvzbz/gv89z044", "ark:/21198/zz002dvzd0/1z05x20r",
            "ark:/21198/zz002dvzd0/7m62f33k", "ark:/21198/zz002dvzg1/f651hj2r", "ark:/21198/zz002dvzg1/r3511g9j",
            "ark:/21198/zz002dvzj2/cb44vj8s", "ark:/21198/zz002dvzj2/d205zv9z", "ark:/21198/zz002dvzm3/dt49rv1n",
            "ark:/21198/zz002dvzm3/n057h129", "ark:/21198/zz002dvznm/v885d89g", "ark:/21198/zz002dvznm/x4965s13",
            "ark:/21198/zz002dvzp4/cc05ns6k", "ark:/21198/zz002dvzp4/cj78jn7n", "ark:/21198/zz002dvzr5/20177b2z",
            "ark:/21198/zz002dvzr5/s239jm5t", "ark:/21198/zz002dvzsp/8j89qm79", "ark:/21198/zz002dvzsp/t709tt47",
            "ark:/21198/zz002dvzt6/2h82r127", "ark:/21198/zz002dvzt6/gb807g0k", "ark:/21198/zz002dvzvq/s159h76w",
            "ark:/21198/zz002dvzvq/v197r03n", "ark:/21198/zz002dvzxr/bj05cm5k", "ark:/21198/zz002dvzxr/gs35w78c",
            "ark:/21198/zz002dvzz8/j1923750", "ark:/21198/zz002dvzz8/ts283c77", "ark:/21198/zz002dw00q/tw72qt9n",
            "ark:/21198/zz002dw00q/wv587g72", "ark:/21198/zz002dw038/3d47q812", "ark:/21198/zz002dw038/hn20jk2c",
            "ark:/21198/zz002dw04s/2n472m0j", "ark:/21198/zz002dw04s/6d09kf9c", "ark:/21198/zz002dw059/jv68s059",
            "ark:/21198/zz002dw059/xp84mz2h", "ark:/21198/zz002dw06t/cd13rk60", "ark:/21198/zz002dw06t/v767hf6q",
            "ark:/21198/zz002dw07b/dw94901p", "ark:/21198/zz002dw07b/wb841v87", "ark:/21198/zz002dw08v/4g389p6h",
            "ark:/21198/zz002dw08v/7522xf96", "ark:/21198/zz002dw09c/b302n79r", "ark:/21198/zz002dw09c/sb41d38z",
            "ark:/21198/zz002dw0bw/rz243z3p", "ark:/21198/zz002dw0bw/vj800n6t", "ark:/21198/zz002dw0cd/7n13bz52",
            "ark:/21198/zz002dw0cd/j920wk6s", "ark:/21198/zz002dw0dx/3b385z6g", "ark:/21198/zz002dw0dx/pj32w51t",
            "ark:/21198/zz002dw0ff/9363qd1g", "ark:/21198/zz002dw0ff/qj41tz3p", "ark:/21198/zz002dw0hg/hv55g786",
            "ark:/21198/zz002dw0hg/xv33qk45", "ark:/21198/zz002dw0kh/5x36q13x", "ark:/21198/zz002dw0kh/mg706q3z",
            "ark:/21198/zz002dw0m1/cq45664p", "ark:/21198/zz002dw0m1/ds52c63s", "ark:/21198/zz002dw0nj/9m48ck36",
            "ark:/21198/zz002dw0nj/xm43h270", "ark:/21198/zz002dw0p2/gd57n63s", "ark:/21198/zz002dw0p2/zp63s227",
            "ark:/21198/zz002dw0sm/0t320x3n", "ark:/21198/zz002dw0sm/kr94fw02", "ark:/21198/zz002dw0vn/qc80x930",
            "ark:/21198/zz002dw0vn/tp12gx24", "ark:/21198/zz002dw0w5/6825v807", "ark:/21198/zz002dw0w5/jg13749z",
            "ark:/21198/zz002dw0z6/dt45q09h", "ark:/21198/zz002dw0z6/zg29cr6n", "ark:/21198/zz002dw106/ck243p7f",
            "ark:/21198/zz002dw106/jd42478g", "ark:/21198/zz002dw11q/f547sg7p", "ark:/21198/zz002dw11q/fj440v0w",
            "ark:/21198/zz002dw127/r702wp89", "ark:/21198/zz002dw127/sw53rx47", "ark:/21198/zz002dw13r/0629mj58",
            "ark:/21198/zz002dw13r/m095t25b", "ark:/21198/zz002dw169/21730d9w", "ark:/21198/zz002dw169/x566bx8w",
            "ark:/21198/zz002dw17t/8q11h049", "ark:/21198/zz002dw17t/zw59z801", "ark:/21198/zz002hdsj2"));

    /*
     * Constant classes have private constructors.
     */
    private TestConstants() {
    }

}
