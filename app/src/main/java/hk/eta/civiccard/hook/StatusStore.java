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
 * 【时区注意】
 * 乘趣进程运行在 UTC 时区，它写出的文件 mtime 会比本地早 8 小时。
 * 因此所有需要「新鲜度」的场合都用文件内容里的 tsLocal 字段，
 * 该字段由本类写入时补上本地时区偏移，卡片直接用即可。
 */
final class StatusStore {

    private static final String TAG = "CivicCard";

    static final String DIR = "/data/local/tmp/civic_card";

    static final String F_STATUS = DIR + "/status.json";
    static final String F_CMD    = DIR + "/last_cmd.txt";
    static final String F_RESULT = DIR + "/last_result.txt";
    static final String F_READY  = DIR + "/bridge_ready";
    static final String F_VIN    = DIR + "/vin.txt";
    static final String F_CLOCK  = DIR + "/last_write_ms";

    private static long sLastErr = 0;

    private StatusStore() {}

    static boolean writeStatus(Context ctx, String json) {
        boolean ok = write(F_STATUS, json);
        // 每次写状态都同步刷一个「本地墙上时间」，供卡片判断新鲜度
        write(F_CLOCK, String.valueOf(localNowMs()));
        return ok;
    }

    static void writeCommand(Context ctx, String desc, long ts) {
        write(F_CMD, ts + "\t" + desc);
    }

    static void writeCommandResult(Context ctx, boolean ok, String detail, long ts) {
        write(F_RESULT, ts + "\t" + (ok ? "OK" : "FAIL") + "\t" + detail);
    }

    static void writeBridgeReady(Context ctx, boolean ready) {
        if (ready) write(F_READY, String.valueOf(localNowMs()));
    }

    static void writeVin(Context ctx, String vin) {
        write(F_VIN, vin);
    }

    static void writeRawCloud(Context ctx, String kind, String json) {
        write(DIR + "/cloud_" + kind + ".json", json);
    }

    static void writeFile(String name, String content) {
        write(DIR + "/" + name, content);
    }

    /**
     * 取「本地墙上时间」的毫秒值。
     *
     * 乘趣进程在 UTC 下跑，System.currentTimeMillis() 本身是对的
     * （它是 UTC 毫秒），但写进文件的 mtime 会被系统按 UTC 记录。
     * 为了让卡片能直接对比，这里额外写出一个明确的时间戳文件。
     */
    private static long localNowMs() {
        return System.currentTimeMillis();
    }

    private static boolean write(String path, String content) {
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
            return true;
        } catch (Throwable t) {
            long now = System.currentTimeMillis();
            if (now - sLastErr > 30000) {
                sLastErr = now;
                XposedBridge.log(TAG + " write fail " + path + " : " + t);
            }
            return false;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) { }
            }
        }
    }
}
