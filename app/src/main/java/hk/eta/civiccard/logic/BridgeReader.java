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
 * 乘趣进程写出的文件带 SELinux categories，卡片进程直接读会被拒绝，
 * 因此优先走 root（su）读取。
 *
 * 文件时间统一用 root 的 stat 取，避免 File.lastModified 因权限失败。
 */
public final class BridgeReader {

    private static final String DIR = "/data/local/tmp/civic_card";
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

    /** 读任意桥文件文本 */
    public static String readAllText(String path) {
        return readAll(path);
    }

    public static String readLastResult() {
        return readAll(F_RESULT);
    }

    /**
     * 数据距今多久（毫秒）。
     *
     * 优先用 root stat 取文件 mtime；
     * 全部失败返回 Long.MAX_VALUE。
     */
    public static long statusAgeMs() {
        // 1) root stat
        String out = shell("cat " + DIR + "/last_write_ms");
        if (out != null && !out.isEmpty()) {
            try {
                long sec = Long.parseLong(out.trim());
                if (sec > 0) {
                    // last_write_ms 存的是毫秒，直接比较
                    long age = System.currentTimeMillis() - sec;
                    return age < 0 ? 0 : age;
                }
            } catch (NumberFormatException ignored) { }
        }

        // 2) 退而用 Java 的 file mtime（可能因权限拿不到）
        try {
            File f = new File(F_STATUS);
            long t = f.lastModified();
            if (t > 0) {
                return Math.max(0, System.currentTimeMillis() - t);
            }
        } catch (Throwable ignored) { }

        return Long.MAX_VALUE;
    }

    // ------------------------------------------------------------ 读取

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
