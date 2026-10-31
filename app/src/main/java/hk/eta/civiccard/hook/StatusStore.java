package hk.eta.civiccard.hook;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import de.robv.android.xposed.XposedBridge;

/**
 * 跨进程数据落盘。
 *
 * 【关键】写入卡片自己的私有目录 /data/data/hk.eta.civiccard/files：
 *   - 该目录上下文是 app_data_file，卡片进程天然可读
 *   - 乘趣进程无权限直接写，所以通过 su 写
 *
 * 之前用 /data/local/tmp 失败，是因为那里的文件带 SELinux categories，
 * 两个不同 UID 的应用互相读不到（即使 chmod 666 也不行）。
 */
final class StatusStore {

    private static final String TAG = "CivicCard";

    /** 卡片私有目录 */
    static final String DIR = "/data/data/hk.eta.civiccard/files/bridge";

    static final String F_STATUS = DIR + "/status.json";
    static final String F_CMD    = DIR + "/last_cmd.txt";
    static final String F_RESULT = DIR + "/last_result.txt";
    static final String F_READY  = DIR + "/bridge_ready";
    static final String F_VIN    = DIR + "/vin.txt";

    private static long sLastErr = 0;

    private StatusStore() {}

    static void writeStatus(Context ctx, String json) {
        write(F_STATUS, json, false);
    }

    /** 指令通道需要卡片可写，权限放宽到 666 */
    static void writeCommand(Context ctx, String desc, long ts) {
        write(F_CMD, ts + "\t" + desc, true);
    }

    static void writeCommandResult(Context ctx, boolean ok, String detail, long ts) {
        write(F_RESULT, ts + "\t" + (ok ? "OK" : "FAIL") + "\t" + detail, true);
    }

    static void writeBridgeReady(Context ctx, boolean ready) {
        if (ready) write(F_READY, String.valueOf(System.currentTimeMillis()), true);
    }

    static void writeVin(Context ctx, String vin) {
        write(F_VIN, vin, false);
    }

    static void writeRawCloud(Context ctx, String kind, String json) {
        write(DIR + "/cloud_" + kind + ".json", json, false);
    }

    static void writeFile(String name, String content) {
        write(DIR + "/" + name, content, false);
    }

    /**
     * 通过 su 写入卡片目录。
     *
     * @param worldWritable true 时 chmod 666，供卡片读写（指令通道）
     */
    private static void write(String path, String content, boolean worldWritable) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            java.io.OutputStream os = p.getOutputStream();
            StringBuilder sb = new StringBuilder();
            sb.append("mkdir -p ").append(DIR).append("\n");
            sb.append("chmod 777 ").append(DIR).append("\n");
            // 用 heredoc 避免内容里的特殊字符出问题
            sb.append("cat > '").append(path).append("' <<'ETAEOF'\n");
            sb.append(content).append("\nETAEOF\n");
            sb.append("chmod ").append(worldWritable ? "666" : "644")
              .append(" '").append(path).append("'\n");
            sb.append("exit\n");
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
            p.waitFor();
        } catch (Throwable t) {
            long now = System.currentTimeMillis();
            if (now - sLastErr > 60000) {
                sLastErr = now;
                XposedBridge.log(TAG + " write fail " + path + " : " + t);
            }
        } finally {
            if (p != null) {
                try { p.destroy(); } catch (Throwable ignored) { }
            }
        }
    }
}
