package hk.eta.civiccard.hook;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 从 VehicleConditionInfo 读取车况。
 *
 * 这是 App 解密后真正显示在「车况详情」页的对象，字段都是明文：
 *   frontLeftDoor:Z  frontLeftLock:Z  frontLeftWindow:Z
 *   trunk:Z  hood:Z  continuationMileage:F  totalMileage  voltage
 *   batteryPercentage  temperatureInside  acLeftTemp  ...
 *
 * 采样点用 VehicleConditionInfoExtKt 的调用，或直接 hook 该类的任意方法。
 * 这里选择 hook 所有 getter 中的高频者，外加周期采样。
 */
final class ConditionReader {

    private static final String CLS_COND = "com.ingeek.nokey.component.sdk.VehicleConditionInfo";

    private static volatile boolean sInstalled = false;
    private static long sLastWrite = 0;
    private static String sLastSig = "";

    private ConditionReader() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;
        try {
            Class<?> c = XposedHelpers.findClassIfExists(CLS_COND, cl);
            if (c == null) {
                XposedBridge.log("CivicCard cond not found");
                return;
            }
            // 挂所有返回基本类型的方法当采样点
            int n = 0;
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                Class<?> rt = m.getReturnType();
                if (rt != boolean.class && rt != int.class && rt != float.class
                        && rt != double.class && rt != long.class) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object bean = param.thisObject;
                            if (bean != null) sample(bean);
                        }
                    });
                    n++;
                } catch (Throwable ignored) { }
            }
            XposedBridge.log("CivicCard ConditionReader installed x" + n
                    + " fields=" + c.getDeclaredFields().length);
        } catch (Throwable t) {
            XposedBridge.log("CivicCard ConditionReader fail: " + t);
        }
    }

    private static void sample(Object bean) {
        long now = System.currentTimeMillis();
        if (now - sLastWrite < 1000) return;   // 最多每秒一次

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ts\":").append(now);

        // 门/锁/窗（布尔）
        putBool(sb, bean, "frontLeftDoor");
        putBool(sb, bean, "frontRightDoor");
        putBool(sb, bean, "rearLeftDoor");
        putBool(sb, bean, "rearRightDoor");
        putBool(sb, bean, "frontLeftLock");
        putBool(sb, bean, "frontRightLock");
        putBool(sb, bean, "rearLeftLock");
        putBool(sb, bean, "rearRightLock");
        putBool(sb, bean, "frontLeftWindow");
        putBool(sb, bean, "frontRightWindow");
        putBool(sb, bean, "rearLeftWindow");
        putBool(sb, bean, "rearRightWindow");
        putBool(sb, bean, "trunk");
        putBool(sb, bean, "hood");

        // 数值
        putNum(sb, bean, "continuationMileage", "continuationMileage");
        putNum(sb, bean, "totalMileage", "totalMileage");
        putNum(sb, bean, "voltage", "voltage");
        putNum(sb, bean, "batteryPercentage", "batteryPercentage");
        putNum(sb, bean, "temperatureInside", "temperatureInside");
        putNum(sb, bean, "temperatureOutside", "temperatureOutside");
        putNum(sb, bean, "power", "power");

        sb.append("}");

        String json = sb.toString();
        if (json.equals(sLastSig)) return;
        sLastSig = json;
        sLastWrite = now;

        BridgeServer.get().onConditionInfo(json);
        XposedBridge.log("CivicCard cond sample " + json);
    }

    private static void putBool(StringBuilder sb, Object bean, String name) {
        try {
            Field f = bean.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(bean);
            if (!(v instanceof Boolean)) return;
            int iv = ((Boolean) v) ? 1 : 0;
            sb.append(",\"").append(name).append("\":").append(iv);
        } catch (Throwable ignored) { }
    }

    private static void putNum(StringBuilder sb, Object bean, String name, String out) {
        try {
            Field f = bean.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(bean);
            if (v == null) return;
            double d;
            if (v instanceof Number) {
                d = ((Number) v).doubleValue();
            } else if (v instanceof Boolean) {
                d = ((Boolean) v) ? 1 : 0;
            } else {
                return;
            }
            sb.append(",\"").append(out).append("\":").append(d);
        } catch (Throwable ignored) { }
    }
}
