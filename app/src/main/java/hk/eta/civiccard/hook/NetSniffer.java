package hk.eta.civiccard.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 抓取 OkHttp 请求 URL + 响应，定位车况接口。
 *
 * 记录格式：每行 "URL <方法> <url>"，响应体另存。
 * 用于找出 App 实际请求哪个接口、带什么 header。
 */
final class NetSniffer {

    private static volatile boolean sInstalled = false;
    private static int sHit = 0;
    private static final StringBuilder sUrls = new StringBuilder();
    private static long sLastFlush = 0;

    private NetSniffer() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;

        // 1. 抓请求 URL
        try {
            Class<?> req = XposedHelpers.findClassIfExists("okhttp3.Request", cl);
            if (req != null) {
                XposedBridge.hookAllMethods(req, "url", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object u = param.getResult();
                        if (u == null) return;
                        String url = String.valueOf(u);
                        synchronized (sUrls) {
                            if (sUrls.indexOf(url) < 0) {
                                sUrls.append(url).append('\n');
                                flush();
                            }
                        }
                    }
                });
                XposedBridge.log("CivicCard NetSniffer url hook ok");
            }
        } catch (Throwable t) {
            XposedBridge.log("CivicCard NetSniffer url fail: " + t);
        }

        // 2. 抓响应体
        try {
            Class<?> body = XposedHelpers.findClassIfExists("okhttp3.ResponseBody", cl);
            if (body != null) {
                XposedBridge.hookAllMethods(body, "string", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object ret = param.getResult();
                        if (!(ret instanceof String)) return;
                        String s = (String) ret;
                        if (s.length() < 100) return;
                        if (!looksLikeCar(s)) return;
                        sHit++;
                        StatusStore.writeRawCloud(null, "resp_" + (sHit % 8), s);
                        XposedBridge.log("CivicCard NetSniffer car-resp#" + sHit
                                + " len=" + s.length());
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("CivicCard NetSniffer body fail: " + t);
        }
    }

    private static void flush() {
        long now = System.currentTimeMillis();
        if (now - sLastFlush < 1000) return;
        sLastFlush = now;
        StatusStore.writeFile("urls.txt", sUrls.toString());
    }

    private static boolean looksLikeCar(String s) {
        return s.contains("pstateData") || s.contains("imStateData")
                || s.contains("vehicleStatus") || s.contains("continuationMileage");
    }
}
