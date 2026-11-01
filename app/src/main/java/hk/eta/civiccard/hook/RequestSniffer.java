package hk.eta.civiccard.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 探测「车况详情页」的数据来源。
 *
 * 目标：找出乘趣 App 里门锁 / 车窗 / 车门状态的真实来源。
 * 已知 pstateData 只有电压+总里程+剩余续航，没有这些状态，
 * 所以它们一定走了别的接口或别的字段。
 *
 * 策略：
 *   1. 记录所有 /vehicle 与 /key 相关 URL
 *   2. 记录包含门锁关键词的响应体
 */
final class RequestSniffer {

    private static volatile boolean sInstalled = false;
    private static final StringBuilder sUrls = new StringBuilder();
    private static int sHit = 0;

    /** 门锁相关关键词 */
    private static final String[] LOCK_KEYS = {
            "frontLeftLock", "frontRightLock", "doorLock",
            "lockStatus", "carLock", "vehicleLock", "burglarAlarm"
    };

    private RequestSniffer() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;

        // 1. URL
        try {
            Class<?> reqCls = XposedHelpers.findClassIfExists("okhttp3.Request", cl);
            if (reqCls != null) {
                XposedBridge.hookAllMethods(reqCls, "url", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object u = param.getResult();
                        if (u == null) return;
                        String url = String.valueOf(u);
                        synchronized (sUrls) {
                            if (sUrls.indexOf(url) < 0) {
                                sUrls.append(url).append('\n');
                                StatusStore.writeFile("urls2.txt", sUrls.toString());
                            }
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("CivicCard ReqSniffer url fail: " + t);
        }

        // 2. 响应体（只留含门锁关键词的）
        try {
            Class<?> body = XposedHelpers.findClassIfExists("okhttp3.ResponseBody", cl);
            if (body != null) {
                int n = 0;
                for (String method : new String[]{"string", "bytes"}) {
                    try {
                        XposedBridge.hookAllMethods(body, method, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Object r = param.getResult();
                                String s = null;
                                if (r instanceof String) s = (String) r;
                                else if (r instanceof byte[]) {
                                    try { s = new String((byte[]) r, "UTF-8"); }
                                    catch (Throwable ignored) { }
                                }
                                if (s == null || s.length() < 30) return;
                                if (!hasLockKey(s)) return;
                                sHit++;
                                StatusStore.writeRawCloud(null, "lock_" + (sHit % 5), s);
                                XposedBridge.log("CivicCard LOCK-RESP #" + sHit
                                        + " len=" + s.length());
                            }
                        });
                        n++;
                    } catch (Throwable ignored) { }
                }
                XposedBridge.log("CivicCard ReqSniffer body hooks x" + n);
            }
        } catch (Throwable t) {
            XposedBridge.log("CivicCard ReqSniffer body fail: " + t);
        }
    }

    private static boolean hasLockKey(String s) {
        for (String k : LOCK_KEYS) {
            if (s.contains(k)) return true;
        }
        return false;
    }
}
