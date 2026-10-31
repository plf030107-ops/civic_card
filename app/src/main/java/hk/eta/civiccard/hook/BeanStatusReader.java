package hk.eta.civiccard.hook;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 从 VehicleDetailBean.isCanUseState() 命中点主动读取车况字段。
 *
 * 为什么这样做：setVehicleStatusData 从未被调用（探针确认），
 * 但 isCanUseState 每秒被调用多次且对象一直存活，
 * 说明车况字符串已经在这个 bean 上了，只是写入路径不同。
 * 所以改成"命中时主动读"，不再等 setter。
 */
final class BeanStatusReader {

    private static final String CLS_BEAN = "com.ingeek.jsbridge.bean.db.VehicleDetailBean";

    /** 车况可能的字段，按优先级 */
    private static final String[] STATUS_FIELDS = {
            "vehicleStatusData",
            "vehicleStatusHighFrequency",
            "vehicleStatusLowFrequency",
    };

    private static volatile boolean sInstalled = false;
    private static long sLastLog = 0;

    private BeanStatusReader() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;
        try {
            Class<?> c = XposedHelpers.findClassIfExists(CLS_BEAN, cl);
            if (c == null) {
                XposedBridge.log("CivicCard bean not found");
                return;
            }
            // isCanUseState 高频调用，正好当采样点
            XposedBridge.hookAllMethods(c, "isCanUseState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object bean = param.thisObject;
                    if (bean == null) return;
                    String data = pickStatus(bean);
                    if (data == null || data.length() < 5) {
                        long now = System.currentTimeMillis();
                        if (now - sLastLog > 30000) {
                            sLastLog = now;
                            XposedBridge.log("CivicCard bean: all status fields empty");
                        }
                        return;
                    }
                    BridgeServer.get().onCloudStatus(data);
                }
            });
            XposedBridge.log("CivicCard beanStatusReader installed");
        } catch (Throwable t) {
            XposedBridge.log("CivicCard beanStatusReader fail: " + t);
        }
    }

    /** 按优先级取第一个非空的车况字符串 */
    private static String pickStatus(Object bean) {
        for (String fn : STATUS_FIELDS) {
            try {
                Field f = bean.getClass().getDeclaredField(fn);
                f.setAccessible(true);
                Object v = f.get(bean);
                if (v instanceof String) {
                    String s = (String) v;
                    if (s.length() >= 5 && !"null".equals(s)) return s;
                }
            } catch (Throwable ignored) { }
        }
        return null;
    }
}
