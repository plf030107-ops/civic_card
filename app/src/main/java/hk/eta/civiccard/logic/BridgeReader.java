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
 * 【关键】乘趣进程写出的文件带 SELinux categories（如 c122,c257,...），
 * 卡片进程的 categories 不同（c152,...），直接读会被拒绝。
 * 因此统一走 root（cards 有 su 权限）读取，这是唯一可靠路径。
 */
public final class BridgeReader {

    private static final String DIR = "/data/data/hk.eta.civiccard/files/bridge";
    private static final String F_STATUS = DIR + "/status.json";
    private static final String F_RESULT = DIR + "/last_result.txt";
    private static final String F_READY  = DIR + "/bridge_ready";

    private BridgeReader() {}

    public static boolean isReady() {
        String s = readAll(F_READY);
        return s != null && !s.isEmpty();
    }

    public static VehicleStatus readStatus() {
        String json = readAll(F_STATUS);
        if (json == null || json.isEmpty()) return null;
        return StatusJson.fromJson(json);
    }

    public static String readLastResult() {
        return readAll(F_RESULT);
    }

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

    // ------------------------------------------------------------ 读取

    /** 优先 root，失败退回直读 */
    private static String readAll(String path) {
        String s = shell("cat " + path);
        if (s != null && !s.isEmpty()) return s;
        return readDirect(path);
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
