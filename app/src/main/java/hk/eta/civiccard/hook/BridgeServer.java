package hk.eta.civiccard.hook;

import android.content.Context;

import hk.eta.civiccard.logic.StatusCodec;
import hk.eta.civiccard.logic.StatusJson;
import hk.eta.civiccard.logic.VehicleStatus;

/**
 * 车况/指令中转。
 *
 * 数据通路（乘趣在 XSpace 用户999，卡片在主空间，UID 不同）：
 *   乘趣进程 ──写──> /data/local/tmp/civic_card/ ──读──> 卡片进程(以 root 读)
 *
 * 用文件而非 Binder：Xposed 注入的进程无法新增 AndroidManifest 组件，
 * AIDL Service 声明不出来；文件通道简单、可靠，且完全只读乘趣自身状态。
 */
public final class BridgeServer {

    private static final BridgeServer INSTANCE = new BridgeServer();

    public static BridgeServer get() { return INSTANCE; }

    private Context mCtx;
    private volatile String mLastCommand;
    private volatile boolean mLastOk;
    private volatile String mLastResult;
    private volatile long mLastCmdTs;
    private volatile String mLastStatusJson;
    private volatile String mVin;

    private BridgeServer() {}

    void attach(Context ctx) {
        mCtx = ctx.getApplicationContext();
        StatusStore.writeBridgeReady(mCtx, true);
        HookEntry.log("bridge attached: " + mCtx.getPackageName()
                + " uid=" + android.os.Process.myUid()
                + " -> " + StatusStore.DIR);
        if (mLastStatusJson != null) {
            StatusStore.writeStatus(mCtx, mLastStatusJson);
        }
    }

    // ------------------------------------------------------------ 指令

    void onCommandSent(String desc) {
        mLastCommand = desc;
        mLastCmdTs = System.currentTimeMillis();
        StatusStore.writeCommand(mCtx, desc, mLastCmdTs);
        HookEntry.log("bridge: cmd sent " + desc);
    }

    void onCommandResult(boolean ok, String detail) {
        mLastOk = ok;
        mLastResult = detail;
        StatusStore.writeCommandResult(mCtx, ok, detail, System.currentTimeMillis());
        HookEntry.log("bridge: cmd result ok=" + ok + " " + detail);
    }

    // ------------------------------------------------------------ 车况

    void onRawStatusFrame(byte[] frame) {
        try {
            VehicleStatus st = StatusCodec.decode(frame);
            if (st == null) {
                HookEntry.log("bridge: frame too short len="
                        + (frame == null ? -1 : frame.length));
                return;
            }
            String json = StatusJson.toJson(st);
            mLastStatusJson = json;
            StatusStore.writeStatus(mCtx, json);
            HookEntry.log("bridge: status " + json);
        } catch (Throwable t) {
            HookEntry.log("bridge: decode fail " + t);
        }
    }

    void onRawStatusText(String text) {
        mLastStatusJson = text;
        StatusStore.writeStatus(mCtx, text);
    }

    /**
     * 云端车况（JSON 字符串）。
     * 与 BLE 帧是同一套字段，但以 key/value 形式给出，直接透传给卡片即可。
     */
    void onCloudStatus(String json) {
        try {
            // 先原样存档一份，便于分析真实格式
            StatusStore.writeRawCloud(mCtx, "pick", json);

            VehicleStatus st = StatusJson.fromJson(json);
            if (st != null) {
                st.timestampMs = System.currentTimeMillis();
                String normalized = StatusJson.toJson(st);
                mLastStatusJson = normalized;
                StatusStore.writeStatus(mCtx, normalized);
            } else {
                mLastStatusJson = json;
                StatusStore.writeStatus(mCtx, json);
            }
        } catch (Throwable t) {
            HookEntry.log("bridge: cloud status fail " + t);
        }
    }

    void setVin(String vin) {
        if (vin != null && !vin.isEmpty()) {
            mVin = vin;
            StatusStore.writeVin(mCtx, vin);
        }
    }

    void onCommandExecuting(String detail) {
        StatusStore.writeCommandResult(mCtx, true, "EXECUTING\t" + detail,
                System.currentTimeMillis());
    }

    // ------------------------------------------------------------ 读接口

    public String vin() { return mVin; }
    public String lastCommand() { return mLastCommand; }
    public boolean lastOk() { return mLastOk; }
    public String lastResult() { return mLastResult; }
    public long lastCmdTs() { return mLastCmdTs; }
    public String lastStatusJson() { return mLastStatusJson; }
}
