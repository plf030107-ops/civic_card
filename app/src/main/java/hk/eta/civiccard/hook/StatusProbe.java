package hk.eta.civiccard.hook;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 车况探针：把可疑的车况入口全部挂上，看哪个真的被调用。
 * 命中后会记录类名+方法名+参数预览，用来定位真实数据流。
 */
final class StatusProbe {

    /** 已知/可疑的车况相关类 */
    private static final String[] CANDIDATE_CLASSES = {
            "com.ingeek.jsbridge.bean.db.VehicleDetailBean",
            "com.ingeek.jsbridge.bean.db.VehicleDetailBeanTable",
            "com.ingeek.nokeeu.key.listener.VehicleCommandListener",
            "com.ingeek.nokey.ui.v2.control.VehicleCommandVisibleLiveData",
            "com.ingeek.nokeeu.key.business.bean.IngeekVehicleProperty",
    };

    private StatusProbe() {}

    static void install(ClassLoader cl) {
        for (String cn : CANDIDATE_CLASSES) {
            try {
                Class<?> c = XposedHelpers.findClassIfExists(cn, cl);
                if (c == null) {
                    log("miss " + cn);
                    continue;
                }
                int n = 0;
                for (Method m : c.getDeclaredMethods()) {
                    String name = m.getName();
                    // 只关心车况/状态相关的
                    if (!isInteresting(name)) continue;
                    try {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                log("HIT " + c.getSimpleName() + "." + name
                                        + " args=" + preview(param.args));
                            }
                        });
                        n++;
                    } catch (Throwable ignored) { }
                }
                if (n > 0) log("probe " + c.getSimpleName() + " x" + n);
            } catch (Throwable t) {
                log("probe fail " + cn + " : " + t);
            }
        }
    }

    private static boolean isInteresting(String name) {
        String s = name.toLowerCase();
        return s.contains("status") || s.contains("state") || s.contains("lock")
                || s.contains("door") || s.contains("window") || s.contains("battery")
                || s.contains("mileage") || s.contains("temp");
    }

    private static String preview(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length && i < 3; i++) {
            if (i > 0) sb.append(", ");
            Object a = args[i];
            if (a == null) { sb.append("null"); continue; }
            if (a instanceof byte[]) {
                byte[] b = (byte[]) a;
                sb.append("byte[").append(b.length).append("]");
            } else if (a instanceof String) {
                String s = (String) a;
                sb.append(s.length() > 60 ? s.substring(0, 60) + "..." : s);
            } else {
                sb.append(a.getClass().getSimpleName());
            }
        }
        return sb.append("]").toString();
    }

    private static void log(String s) {
        XposedBridge.log("CivicCardProbe " + s);
    }
}
