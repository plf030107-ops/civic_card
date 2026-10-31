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
 * 【为什么直接写，不走 su】
 * 实测乘趣进程虽然进了 KernelSU 白名单，但 hook 里 Runtime.exec("su")
 * 仍被拒绝（进程未以交互身份启动）。而目标目录权限是 777、
 * 上下文 app_data_file，直写就能成功。
 *
 * 【为什么写卡片的私有目录】
 * /data/local/tmp 下的文件带 SELinux categories（c122 vs c152），
 * 两个不同 UID 的应用即使 chmod 666 也互不相通。
 * 卡片自己的 files/bridge 是双方唯一都可达的位置。
 */
final class StatusStore {

    private static final String TAG = "CivicCard";

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

    private static void write(String path, String content, boolean worldWritable) {
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
            long now = System.currentTimeMillis();
            if (now - sLastErr > 30000) {
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
