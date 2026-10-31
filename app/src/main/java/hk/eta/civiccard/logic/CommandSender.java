package hk.eta.civiccard.logic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * 卡片 -> 乘趣 的指令通道。
 *
 * 链路：
 *   卡片写 /data/local/tmp/civic_card/cmd_req
 *   -> 乘趣进程内 FileObserver 捕获
 *   -> Builder.setName(alias).create() -> XKeyApi.sendVehicleCommand
 *
 * 【权限现实】/data/local/tmp 普通 App 无权写，必须走 root。
 * 因此写入优先用 su，失败再退回直接文件写。
 */
public final class CommandSender {

    public static final String DIR   = "/data/local/tmp/civic_card";
    public static final String F_REQ = DIR + "/cmd_req";
    public static final String F_RES = DIR + "/local_result.txt";

    /** 乘趣 9 条指令别名，与 assets/command_timeout_config.json 一致 */
    public static final String CMD_LOCK         = "lock";
    public static final String CMD_UNLOCK       = "unlock";
    public static final String CMD_OPEN_TRUNK   = "openTrunk";
    public static final String CMD_CLOSE_TRUNK  = "closeTrunk";
    public static final String CMD_START_ENGINE = "startEngine";
    public static final String CMD_STOP_ENGINE  = "stopEngine";
    public static final String CMD_OPEN_WINDOW  = "openWindow";
    public static final String CMD_CLOSE_WINDOW = "closeWindow";
    public static final String CMD_SEARCH       = "search";

    private CommandSender() {}

    /**
     * 发指令。
     * @param alias 上列常量之一
     * @return true 表示已成功投递到通道
     */
    public static boolean send(String alias) {
        // 前置校验，和乘趣 common_precondition 保持一致
        VehicleStatus st = BridgeReader.readStatus();
        if (st != null) {
            String block = st.preconditionBlockReason(alias);
            if (block != null) {
                writeLocalResult("BLOCKED\t" + alias + "\t" + block);
                return false;
            }
        }

        boolean ok = writeViaRoot(alias);
        if (!ok) {
            ok = writeDirect(alias);
        }
        if (!ok) {
            writeLocalResult("ERROR\t" + alias + "\t通道写入失败");
        }
        return ok;
    }

    /** root 通道：最可靠 */
    private static boolean writeViaRoot(String alias) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            OutputStreamWriter w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8);
            w.write("mkdir -p " + DIR + "\n");
            w.write("chmod 777 " + DIR + "\n");
            w.write("printf '%s' '" + alias + "' > " + F_REQ + "\n");
            w.write("chmod 666 " + F_REQ + "\n");
            w.write("exit\n");
            w.flush();
            w.close();
            int rc = p.waitFor();
            return rc == 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (p != null) {
                try { p.destroy(); } catch (Throwable ignored) { }
            }
        }
    }

    /** 直写：若进程本身有权限（例如以后改成常驻 root 守护） */
    private static boolean writeDirect(String alias) {
        FileOutputStream fos = null;
        try {
            File dir = new File(DIR);
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            File tmp = new File(F_REQ + ".tmp");
            fos = new FileOutputStream(tmp);
            fos.write(alias.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.getFD().sync();
            fos.close();
            fos = null;
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(new File(F_REQ));
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Throwable ignored) { }
            }
        }
    }

    /** 卡片侧看到的前置校验失败原因，写在另一个文件避免和乘趣的结果混 */
    private static void writeLocalResult(String s) {
        try {
            FileOutputStream fos = new FileOutputStream(F_RES);
            fos.write(s.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Throwable ignored) { }
    }
}
