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
 * 通道：/data/local/tmp/civic_card/  （777，乘趣和卡片都能访问）
 *
 * 注意：本类跑在乘趣进程（u0_a378），非 root。
 * setReadable/setWritable 在非 owner 时会抛 SecurityException，
 * 所以每一步都单独 try，失败只记日志不中断。
 */
final class StatusStore {

    private static final String TAG = "CivicCard";

    static final String DIR      = "/data/local/tmp/civic_card";
    static final String F_STATUS = DIR + "/status.json";
    static final String F_CMD    = DIR + "/last_cmd.txt";
    static final String F_RESULT = DIR + "/last_result.txt";
    static final String F_READY  = DIR + "/bridge_ready";
    static final String F_VIN    = DIR + "/vin.txt";

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

    /** 云端原始字符串，用于分析字段格式 */
    static void writeRawCloud(Context ctx, String json) {
        write(DIR + "/cloud_raw.json", json);
    }

    private static void write(String path, String content) {
        try {
            ensureDir();
        } catch (Throwable t) {
            log("ensureDir fail: " + t);
        }

        FileOutputStream fos = null;
        try {
            File tmp = new File(path + ".tmp");
            fos = new FileOutputStream(tmp);
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.getFD().sync();
            fos.close();
            fos = null;

            File dst = new File(path);
            if (dst.exists() && !dst.delete()) {
                log("delete old fail: " + path);
            }
            if (!tmp.renameTo(dst)) {
                log("rename fail: " + path);
                // 退一步：直接覆盖写
                FileOutputStream d = new FileOutputStream(dst);
                d.write(content.getBytes(StandardCharsets.UTF_8));
                d.flush();
                d.close();
            }

            // 单独尝试放宽权限，失败无妨（同 UID 可读）
            try { dst.setReadable(true, false); } catch (Throwable ignored) { }
            try { dst.setWritable(true, false); } catch (Throwable ignored) { }
        } catch (Throwable t) {
            log("write fail " + path + " : " + t);
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) { }
            }
        }
    }

    private static void ensureDir() {
        File dir = new File(DIR);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                log("mkdir " + DIR);
            }
        }
        if (!dir.canWrite()) {
            log("dir not writable: " + DIR);
        }
    }

    private static void log(String s) {
        XposedBridge.log(TAG + " " + s);
    }
}
