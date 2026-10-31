package hk.eta.civiccard.logic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 卡片侧读取桥数据。
 *
 * /data/local/tmp/civic_card 是乘趣进程（u0_a378）写入的，
 * 卡片进程 UID 不同，直接读可能被拒。优先走 root cat，
 * 失败再退回普通 File 读。
 */
public final class BridgeReader {

    private static final String DIR = "/data/local/tmp/civic_card";
    private static final String F_STATUS = DIR + "/status.json";
    private static final String F_RESULT = DIR + "/last_result.txt";
    private static final String F_READY  = DIR + "/bridge_ready";

    private BridgeReader() {}

    /** 桥是否就绪（乘趣进程 hook 加载成功） */
    public static boolean isReady() {
        String s = readAll(F_READY);
        return s != null && !s.isEmpty();
    }

    /** 读车况，读不到返回 null */
    public static VehicleStatus readStatus() {
        String json = readAll(F_STATUS);
        if (json == null || json.isEmpty()) return null;
        return StatusJson.fromJson(json);
    }

    /** 最近一次指令结果原始行 */
    public static String readLastResult() {
        return readAll(F_RESULT);
    }

    /** 车况最后更新时间，Long.MAX_VALUE 表示没有 */
    public static long statusAgeMs() {
        String s = shell("stat -c %Y " + F_STATUS);
        if (s == null) return Long.MAX_VALUE;
        try {
            long sec = Long.parseLong(s.trim());
            if (sec <= 0) return Long.MAX_VALUE;
            return System.currentTimeMillis() - sec * 1000L;
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    // ------------------------------------------------------------ 读取实现

    private static String readAll(String path) {
        // 先生成普通读
        String s = readDirect(path);
        if (s != null && !s.isEmpty()) return s;
        // 退回 root
        return shell("cat " + path);
    }

    private static String readDirect(String path) {
        File f = new File(path);
        if (!f.exists() || !f.canRead()) return null;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (IOException e) {
            return null;
        } finally {
            if (br != null) try { br.close(); } catch (IOException ignored) { }
        }
    }

    /** 以 root 执行一条 shell 命令并取 stdout */
    private static String shell(String cmd) {
        Process p = null;
        BufferedReader br = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            p.waitFor();
            return sb.length() == 0 ? null : sb.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            if (br != null) try { br.close(); } catch (IOException ignored) { }
            if (p != null) {
                try { p.destroy(); } catch (Throwable ignored) { }
            }
        }
    }
}
