package hk.eta.civiccard.hook;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 跨进程数据落盘。
 *
 * 通道选择说明：
 *  乘趣跑在 XSpace(用户999) 里，卡片跑在主空间，两者 UID 不同。
 *  Binder/AIDL 需要服务端声明组件且能被 bind，Xposed 模块注入的进程
 *  不能新增 AndroidManifest 组件，因此 Binder 走不通。
 *
 *  这里用「共享目录 + 文件」：卡片侧用 Root 读，简单可靠，
 *  且天然满足"只读，不改乘趣"。
 *
 * 目录：/data/local/tmp/civic_card/  权限 0777，两边都能访问。
 */
final class StatusStore {

    static final String DIR = "/data/local/tmp/civic_card";
    static final String F_STATUS = DIR + "/status.json";
    static final String F_CMD = DIR + "/last_cmd.txt";
    static final String F_RESULT = DIR + "/last_result.txt";
    static final String F_READY = DIR + "/bridge_ready";

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
        write(DIR + "/vin.txt", vin);
    }

    private static void write(String path, String content) {
        FileOutputStream fos = null;
        try {
            File dir = new File(DIR);
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                //noinspection ResultOfMethodCallIgnored
                dir.setReadable(true, false);
                //noinspection ResultOfMethodCallIgnored
                dir.setWritable(true, false);
                //noinspection ResultOfMethodCallIgnored
                dir.setExecutable(true, false);
            }
            // 先写临时文件再 rename，避免卡片读到半截内容
            File tmp = new File(path + ".tmp");
            fos = new FileOutputStream(tmp);
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.getFD().sync();
            fos.close();
            fos = null;
            File dst = new File(path);
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(dst);
            // 关键：放宽权限，否则卡片进程（不同 UID）读不到
            //noinspection ResultOfMethodCallIgnored
            dst.setReadable(true, false);
            //noinspection ResultOfMethodCallIgnored
            dst.setWritable(true, false);
            //noinspection ResultOfMethodCallIgnored
            dst.setExecutable(false, false);
        } catch (Throwable ignored) {
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) { }
            }
        }
    }
}
