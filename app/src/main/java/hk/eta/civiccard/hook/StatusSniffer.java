package hk.eta.civiccard.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import hk.eta.civiccard.logic.PStateDecoder;

/**
 * 车况抓取：拦截 getVehicleState 响应，解析 pstateData。
 *
 * 为什么这样最合适：
 *   1. 这个接口只在打开乘趣时请求一次，不产生额外流量、不唤醒车辆
 *   2. 数据是云端下发的真实状态，不依赖蓝牙连接
 *   3. pstateData 字节布局已用真实车辆数据反推验证
 */
final class StatusSniffer {

    private static volatile boolean sInstalled = false;
    private static String sLastSig = "";

    private StatusSniffer() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;
        try {
            Class<?> body = XposedHelpers.findClassIfExists("okhttp3.ResponseBody", cl);
            if (body == null) {
                XposedBridge.log("CivicCard StatusSniffer no ResponseBody");
                return;
            }
            XposedBridge.hookAllMethods(body, "string", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object ret = param.getResult();
                    if (!(ret instanceof String)) return;
                    String s = (String) ret;
                    // 只关心车况接口
                    if (!s.contains("pstateData")) return;
                    handle(s);
                }
            });
            XposedBridge.log("CivicCard StatusSniffer installed");
        } catch (Throwable t) {
            XposedBridge.log("CivicCard StatusSniffer fail: " + t);
        }
    }

    private static void handle(String json) {
        try {
            PStateDecoder.Result r = PStateDecoder.fromResponseJson(json);
            if (r == null || !r.valid) {
                XposedBridge.log("CivicCard StatusSniffer decode null");
                return;
            }
            String sig = r.totalMileage + "/" + r.remainMileage + "/" + r.voltageRaw;
            if (sig.equals(sLastSig)) return;   // 未变化不重复写
            sLastSig = sig;

            String statusJson = PStateDecoder.toStatusJson(r);
            StatusStore.writeStatus(null, statusJson);
            XposedBridge.log("CivicCard STATUS v=" + r.voltage
                    + " total=" + r.totalMileage
                    + " remain=" + r.remainMileage
                    + " st=" + r.statusByte);
        } catch (Throwable t) {
            XposedBridge.log("CivicCard StatusSniffer handle fail: " + t);
        }
    }
}
