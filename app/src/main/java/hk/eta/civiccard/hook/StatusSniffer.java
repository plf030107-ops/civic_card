package hk.eta.civiccard.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import hk.eta.civiccard.logic.PStateDecoder;

/**
 * 车况抓取：拦截 getVehicleState 响应体，解析 pstateData。
 *
 * 上一版只挂 ResponseBody.string()，实测 App 并不走 string()，
 * 所以这里同时挂多个读取入口：
 *   - bytes()      常见
 *   - source()     Okio 缓冲
 *   - string()
 *
 * 都统一在拿到内容后交给同一个 handler。
 */
final class StatusSniffer {

    private static volatile boolean sInstalled = false;
    private static String sLastSig = "";
    private static int sHit = 0;

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

            int n = 0;

            // string()
            XposedBridge.hookAllMethods(body, "string", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object r = param.getResult();
                    if (r instanceof String) handle((String) r);
                }
            });
            n++;

            // bytes()
            XposedBridge.hookAllMethods(body, "bytes", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object r = param.getResult();
                    if (r instanceof byte[]) {
                        try {
                            handle(new String((byte[]) r, "UTF-8"));
                        } catch (Throwable ignored) { }
                    }
                }
            });
            n++;

            XposedBridge.log("CivicCard StatusSniffer installed x" + n);
        } catch (Throwable t) {
            XposedBridge.log("CivicCard StatusSniffer fail: " + t);
        }
    }

    private static void handle(String s) {
        if (s == null || s.length() < 50) return;
        if (!s.contains("pstateData")) return;

        sHit++;
        try {
            PStateDecoder.Result r = PStateDecoder.fromResponseJson(s);
            if (r == null || !r.valid) {
                XposedBridge.log("CivicCard STATUS decode null #" + sHit);
                return;
            }
            String sig = r.totalMileage + "/" + r.remainMileage + "/" + r.voltageRaw;
            boolean changed = !sig.equals(sLastSig);
            sLastSig = sig;

            if (changed) {
                StatusStore.writeStatus(null, PStateDecoder.toStatusJson(r));
            }
            XposedBridge.log("CivicCard STATUS #" + sHit
                    + " v=" + r.voltage
                    + " total=" + r.totalMileage
                    + " remain=" + r.remainMileage
                    + " st=" + r.statusByte
                    + (changed ? " CHANGED" : ""));
        } catch (Throwable t) {
            XposedBridge.log("CivicCard STATUS fail: " + t);
        }
    }
}
