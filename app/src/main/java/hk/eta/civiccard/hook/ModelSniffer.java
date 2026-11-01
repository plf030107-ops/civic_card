package hk.eta.civiccard.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 抓车模图 URL。
 *
 * 来源：/v5/detail 返回体里的 vehicleModelPicture 字段，
 *   例 https://p1.nokeeu.com/carmodel/30ce0b627f0141d8be2932172658b97d.png
 *
 * 从 HTTP 响应体里用正则取出，写到 model_url.txt，卡片侧下载缓存。
 */
final class ModelSniffer {

    private static volatile boolean sInstalled = false;
    private static String sLast = null;

    private ModelSniffer() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;
        try {
            Class<?> body = XposedHelpers.findClassIfExists("okhttp3.ResponseBody", cl);
            if (body == null) return;
            XposedBridge.hookAllMethods(body, "string", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object r = param.getResult();
                    if (!(r instanceof String)) return;
                    String s = (String) r;
                    if (!s.contains("vehicleModelPicture")) return;
                    String url = extract(s);
                    if (url == null) return;
                    if (url.equals(sLast)) return;
                    sLast = url;
                    StatusStore.writeFile("model_url.txt", url);
                    XposedBridge.log("CivicCard MODEL_URL " + url);
                }
            });
            XposedBridge.log("CivicCard ModelSniffer installed");
        } catch (Throwable t) {
            XposedBridge.log("CivicCard ModelSniffer fail: " + t);
        }
    }

    /** 从 JSON 文本里取 vehicleModelPicture 的值 */
    private static String extract(String s) {
        String key = "\"vehicleModelPicture\":\"";
        int i = s.indexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        int end = s.indexOf('"', start);
        if (end <= start) return null;
        String v = s.substring(start, end);
        if (!v.startsWith("http")) return null;
        return v;
    }
}
