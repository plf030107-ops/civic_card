package hk.eta.civiccard.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 抓取 OkHttp 请求头和请求体，还原独立调用所需格式。
 *
 * 只关心车况接口（vehicle/a/v* 与 getVehicleState），避免刷屏。
 */
final class RequestSniffer {

    private static volatile boolean sInstalled = false;
    private static final StringBuilder sBuf = new StringBuilder();
    private static int sCount = 0;

    private RequestSniffer() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;

        try {
            Class<?> reqCls = XposedHelpers.findClassIfExists("okhttp3.Request", cl);
            if (reqCls == null) {
                XposedBridge.log("CivicCard ReqSniffer no Request");
                return;
            }

            XposedBridge.hookAllMethods(reqCls, "url", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object u = param.getResult();
                    if (u == null) return;
                    String url = String.valueOf(u);
                    if (!url.contains("/vehicle/a/")) return;

                    Object req = param.thisObject;
                    StringBuilder sb = new StringBuilder();
                    sb.append("=== ").append(url).append(" ===\n");

                    // 方法
                    try {
                        Field mf = reqCls.getDeclaredField("method");
                        mf.setAccessible(true);
                        sb.append("method: ").append(mf.get(req)).append('\n');
                    } catch (Throwable ignored) { }

                    // headers
                    try {
                        Field hf = reqCls.getDeclaredField("headers");
                        hf.setAccessible(true);
                        Object hs = hf.get(req);
                        if (hs != null) {
                            for (java.lang.reflect.Method m : hs.getClass().getDeclaredMethods()) {
                                if (!"toMultimap".equals(m.getName())) continue;
                                m.setAccessible(true);
                                Object mm = m.invoke(hs);
                                if (mm instanceof Map) {
                                    for (Object e : ((Map<?, ?>) mm).entrySet()) {
                                        sb.append("H: ").append(e).append('\n');
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        sb.append("headers fail: ").append(t).append('\n');
                    }

                    // body
                    try {
                        Field bf = reqCls.getDeclaredField("body");
                        bf.setAccessible(true);
                        Object body = bf.get(req);
                        if (body != null) {
                            sb.append("body: ").append(String.valueOf(body)).append('\n');
                        }
                    } catch (Throwable ignored) { }

                    append(sb.toString());
                }
            });
            XposedBridge.log("CivicCard ReqSniffer installed");
        } catch (Throwable t) {
            XposedBridge.log("CivicCard ReqSniffer fail: " + t);
        }
    }

    private static synchronized void append(String s) {
        if (sBuf.length() > 100000) sBuf.setLength(0);
        sBuf.append(s);
        sCount++;
        StatusStore.writeFile("requests.txt", sBuf.toString());
        if (sCount % 5 == 1) {
            XposedBridge.log("CivicCard ReqSniffer #" + sCount);
        }
    }
}
