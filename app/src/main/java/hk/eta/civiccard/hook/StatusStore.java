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
 * 写卡片的私有目录 /data/local/tmp/civic_card：
 *   - 卡片进程天然可读
 *   - 乘趣进程需要该路径的父目录有 x 权限且 SELinux 无 categories 限制
 *     （已在部署脚本里处理：chmod 711 父目录 + chcon 去 categories）
 */
final class StatusStore {

    private static final String TAG = "CivicCard";

    static final String DIR = "/data/local/tmp/civic_card";

    static final String F_STATUS = DIR + "/status.json";
    static final String F_CMD    = DIR + "/last_cmd.txt";
    static final String F_RESULT = DIR + "/last_result.txt";
    static final String F_READY  = DIR + "/bridge_ready";
    static final String F_VIN    = DIR + "/vin.txt";

    private static long sLastErr = 0;

    private StatusStore() {}

    static boolean writeStatus(Context ctx, String json) {
        return write(F_STATUS, json);
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

    static void writeRawCloud(Context ctx, String kind, String json) {
        write(DIR + "/cloud_" + kind + ".json", json);
    }

    static void writeFile(String name, String content) {
        write(DIR + "/" + name, content);
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
