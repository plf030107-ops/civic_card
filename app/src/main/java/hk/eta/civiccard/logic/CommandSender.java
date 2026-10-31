package hk.eta.civiccard.logic;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 卡片 -> 乘趣 的指令通道。
 *
 * 路径在卡片自己的私有目录，卡片可直接写。
 * 乘趣侧通过 FileObserver 监听该目录。
 */
public final class CommandSender {

    /** 卡片私有目录，与 hook 侧 StatusStore 保持一致 */
    public static final String DIR   = "/data/data/hk.eta.civiccard/files/bridge";
    public static final String F_REQ = DIR + "/cmd_req";
    public static final String F_RES = DIR + "/local_result.txt";

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

    public static boolean send(String alias) {
        VehicleStatus st = BridgeReader.readStatus();
        if (st != null) {
            String block = st.preconditionBlockReason(alias);
            if (block != null) {
                writeLocalResult("BLOCKED\t" + alias + "\t" + block);
                return false;
            }
        }
        return writeDirect(alias);
    }

    private static boolean writeDirect(String alias) {
        FileOutputStream fos = null;
        try {
            File dir = new File(DIR);
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            fos = new FileOutputStream(F_REQ, false);
            fos.write(alias.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            return true;
        } catch (Throwable t) {
            writeLocalResult("ERROR\t" + alias + "\t" + t);
            return false;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Throwable ignored) { }
            }
        }
    }

    private static void writeLocalResult(String s) {
        try {
            FileOutputStream fos = new FileOutputStream(F_RES, false);
            fos.write(s.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Throwable ignored) { }
    }
}
