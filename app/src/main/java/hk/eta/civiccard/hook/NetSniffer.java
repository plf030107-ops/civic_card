package hk.eta.civiccard.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 网络响应抓取器。
 *
 * 车况数值既不在数据库、也不在 bean 字段里，只能来自云端接口响应。
 * 这里 hook OkHttp 的 ResponseBody.string()，把可疑的响应体落盘。
 *
 * 只关注包含车况关键词的响应，避免刷爆磁盘。
 */
final class NetSniffer {

    private static volatile boolean sInstalled = false;
    private static long sLastLog = 0;
    private static int sHit = 0;

    /** 命中这些关键词就认为可能是车况响应 */
    private static final String[] KEYS = {
            "totalMileage", "continuationMileage", "frontLeftLock",
            "vehicleStatus", "remainL", "voltage", "batteryPercentage"
    };

    private NetSniffer() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;
        try {
            Class<?> body = XposedHelpers.findClassIfExists("okhttp3.ResponseBody", cl);
            if (body == null) {
                XposedBridge.log("CivicCardSniff okhttp not found");
                return;
            }
            XposedBridge.hookAllMethods(body, "string", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object ret = param.getResult();
                    if (!(ret instanceof String)) return;
                    String s = (String) ret;
                    if (s.length() < 30) return;
                    sHit++;
                    // 不过滤，全部留档，便于事后定位车况接口
                    StatusStore.writeRawCloud(null, "all_" + (sHit % 20), s);
                    if (looksLikeStatus(s)) {
                        StatusStore.writeRawCloud(null, "hit_" + (sHit % 10), s);
                        XposedBridge.log("CivicCardSniff HIT#" + sHit + " len=" + s.length());
                    }
                }
            });
            XposedBridge.log("CivicCardSniff installed");
        } catch (Throwable t) {
            XposedBridge.log("CivicCardSniff fail: " + t);
        }
    }

    private static boolean looksLikeStatus(String s) {
        // 必须同时出现两个以上车况字段，避免误报
        int c = 0;
        for (String k : KEYS) {
            if (s.contains(k)) c++;
            if (c >= 2) return true;
        }
        return false;
    }
}
