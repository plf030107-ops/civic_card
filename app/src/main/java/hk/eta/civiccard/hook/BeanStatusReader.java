package hk.eta.civiccard.hook;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 从 VehicleDetailBean 主动读车况。
 *
 * 三个候选字段分开落盘，便于定位哪个存的是实际数值：
 *   vehicleStatusData           - 实测是协议定义（items 说明），不是数值
 *   vehicleStatusHighFrequency  - 高频状态
 *   vehicleStatusLowFrequency   - 低频状态
 *
 * 采样点用 isCanUseState()，它每秒被调用多次且对象常驻。
 */
final class BeanStatusReader {

    private static final String CLS_BEAN = "com.ingeek.jsbridge.bean.db.VehicleDetailBean";

    private static volatile boolean sInstalled = false;
    private static long sLastLog = 0;
    private static String sLastData = "";
    private static String sLastHigh = "";
    private static String sLastLow = "";

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
            XposedBridge.hookAllMethods(c, "isCanUseState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object bean = param.thisObject;
                    if (bean == null) return;
                    sample(bean);
                }
            });
            XposedBridge.log("CivicCard beanStatusReader installed");
        } catch (Throwable t) {
            XposedBridge.log("CivicCard beanStatusReader fail: " + t);
        }
    }

    private static void sample(Object bean) {
        String data = str(bean, "vehicleStatusData");
        String high = str(bean, "vehicleStatusHighFrequency");
        String low  = str(bean, "vehicleStatusLowFrequency");

        boolean changed = false;

        if (data != null && !data.equals(sLastData)) {
            sLastData = data;
            StatusStore.writeRawCloud(null, "data", data);
            changed = true;
        }
        if (high != null && !high.equals(sLastHigh)) {
            sLastHigh = high;
            StatusStore.writeRawCloud(null, "high", high);
            changed = true;
        }
        if (low != null && !low.equals(sLastLow)) {
            sLastLow = low;
            StatusStore.writeRawCloud(null, "low", low);
            changed = true;
        }

        if (changed) {
            XposedBridge.log("CivicCard bean changed: data=" + len(data)
                    + " high=" + len(high) + " low=" + len(low));
        }

        long now = System.currentTimeMillis();
        if (now - sLastLog > 60000) {
            sLastLog = now;
            XposedBridge.log("CivicCard bean sample: data=" + len(data)
                    + " high=" + len(high) + " low=" + len(low));
        }

        // 优先用高频/低频（更像真实数值），都没有才退回 data
        String pick = null;
        if (high != null && high.length() > 5) pick = high;
        else if (low != null && low.length() > 5) pick = low;

        if (pick != null) {
            BridgeServer.get().onCloudStatus(pick);
        }
    }

    private static int len(String s) {
        return s == null ? -1 : s.length();
    }

    private static String str(Object bean, String field) {
        try {
            Field f = bean.getClass().getDeclaredField(field);
            f.setAccessible(true);
            Object v = f.get(bean);
            if (v instanceof String) {
                String s = (String) v;
                if (s.length() >= 5 && !"null".equals(s)) return s;
            }
        } catch (Throwable ignored) { }
        return null;
    }
}
