package hk.eta.civiccard.hook;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import de.robv.android.xposed.XposedBridge;

/**
 * 跨进程数据落盘。
 *
 * 直接覆盖写，不做 tmp+rename：
 * 实测 rename 在并发写同一文件时会失败，且失败日志会刷爆 LSPosed 日志。
 *
 * 卡片侧读取时容忍半截内容（JSON 解析失败就跳过），所以不需要原子性。
 */
final class StatusStore {

    private static final String TAG = "CivicCard";

    static final String DIR      = "/data/local/tmp/civic_card";
    static final String F_STATUS = DIR + "/status.json";
    static final String F_CMD    = DIR + "/last_cmd.txt";
    static final String F_RESULT = DIR + "/last_result.txt";
    static final String F_READY  = DIR + "/bridge_ready";
    static final String F_VIN    = DIR + "/vin.txt";

    private static long sLastErr = 0;

    private StatusStore() {}

    static void writeStatus(Context ctx, String json) {
        write(F_STATUS, json);
    }

    static void writeCommand(Context ctx, String desc, long ts) {
        write(F_CMD, ts + "\t" + desc);
    }

    static void writeCommandResult(Context ctx, boolean ok, String detail, long ts) {
        write(F_RESULT, ts + "\t" + (ok ? "OK" : "FAIL") + "\t" + detail);
    }

    static void writeBridgeReady(Context ctx, boolean ready) {
        if (ready) write(F_READY, String.valueOf(System.currentTimeMillis()));
    }

    static void writeVin(Context ctx, String vin) {
        write(F_VIN, vin);
    }

    /** 云端原始字符串，按类型分文件存 */
    static void writeRawCloud(Context ctx, String kind, String json) {
        write(DIR + "/cloud_" + kind + ".json", json);
    }

    private static void write(String path, String content) {
        FileOutputStream fos = null;
        try {
            File dir = new File(DIR);
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            fos = new FileOutputStream(path, false);
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Throwable t) {
            // 限流：同一分钟内只报一次，避免刷屏
            long now = System.currentTimeMillis();
            if (now - sLastErr > 60000) {
                sLastErr = now;
                XposedBridge.log(TAG + " write fail " + path + " : " + t);
            }
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) { }
            }
        }
    }
}
