package hk.eta.civiccard.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 全量抓包：记录每个请求 URL 及其响应体，一一对应落盘。
 *
 * 目的：找出乘趣「车况详情页」里门锁/车窗/车门等状态的真实来源。
 * 之前只抓了 ResponseBody.string()，很可能 App 用别的读取方式（bytes/source/peekBody），
 * 导致漏抓。这里把 url + 响应体成对保存，便于事后比对。
 */
final class NetDump {

    private static volatile boolean sInstalled = false;
    private static int sSeq = 0;

    private NetDump() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;

        // 记录 URL → 存在线程变量，供响应体配对
        final ThreadLocal<String> lastUrl = new ThreadLocal<>();

        try {
            Class<?> reqCls = XposedHelpers.findClassIfExists("okhttp3.Request", cl);
            if (reqCls != null) {
                XposedBridge.hookAllMethods(reqCls, "url", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object u = param.getResult();
                        if (u != null) lastUrl.set(String.valueOf(u));
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("CivicCard NetDump url fail: " + t);
        }

        try {
            Class<?> body = XposedHelpers.findClassIfExists("okhttp3.ResponseBody", cl);
            if (body == null) {
                XposedBridge.log("CivicCard NetDump no ResponseBody");
                return;
            }
            int n = 0;
            for (String m : new String[]{"string", "bytes"}) {
                try {
                    XposedBridge.hookAllMethods(body, m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object r = param.getResult();
                            String s = null;
                            if (r instanceof String) s = (String) r;
                            else if (r instanceof byte[]) {
                                try { s = new String((byte[]) r, "UTF-8"); }
                                catch (Throwable ignored) { }
                            }
                            if (s == null || s.length() < 20) return;

                            String url = lastUrl.get();
                            sSeq++;
                            String head = (url == null ? "?" : url);
                            // 每 20 个循环覆盖，保留最近的
                            StatusStore.writeFile("dump_" + (sSeq % 40) + ".txt",
                                    head + "\n----\n" + s);
                            if (sSeq % 10 == 1) {
                                XposedBridge.log("CivicCard NetDump #" + sSeq + " " + head);
                            }
                        }
                    });
                    n++;
                } catch (Throwable ignored) { }
            }
            XposedBridge.log("CivicCard NetDump installed x" + n);
        } catch (Throwable t) {
            XposedBridge.log("CivicCard NetDump body fail: " + t);
        }
    }
}
