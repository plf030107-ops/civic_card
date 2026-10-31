package hk.eta.civiccard.hook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 从 VehicleConditionInfo 读取车况。
 *
 * 之前挂 getter 全部没触发（App 内部直接读字段，不走 getter），
 * 所以改成：
 *   1. 挂所有构造函数 —— 对象一创建就采样
 *   2. 同时保留 getter 兜底
 *   3. 加一个隐藏入口：卡片进程写入 request_dump 时主动回传一次
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

            // 1. 构造器：对象一创建就采样
            int cn = 0;
            for (Constructor<?> ctor : c.getDeclaredConstructors()) {
                try {
                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object bean = param.thisObject;
                            if (bean != null) {
                                XposedBridge.log("CivicCard cond constructed");
                                sample(bean, true);
                            }
                        }
                    });
                    cn++;
                } catch (Throwable ignored) { }
            }

            // 2. getter 兜底
            int mn = 0;
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
                            if (bean != null) sample(bean, false);
                        }
                    });
                    mn++;
                } catch (Throwable ignored) { }
            }

            XposedBridge.log("CivicCard ConditionReader installed ctor=" + cn
                    + " getter=" + mn + " fields=" + c.getDeclaredFields().length);
        } catch (Throwable t) {
            XposedBridge.log("CivicCard ConditionReader fail: " + t);
        }
    }

    private static void sample(Object bean, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - sLastWrite < 1000) return;

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ts\":").append(now);

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
            sb.append(",\"").append(name).append("\":")
              .append(((Boolean) v) ? 1 : 0);
        } catch (Throwable ignored) { }
    }

    private static void putNum(StringBuilder sb, Object bean, String name, String out) {
        try {
            Field f = bean.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(bean);
            if (v == null) return;
            double d;
            if (v instanceof Number) d = ((Number) v).doubleValue();
            else if (v instanceof Boolean) d = ((Boolean) v) ? 1 : 0;
            else return;
            sb.append(",\"").append(out).append("\":").append(d);
        } catch (Throwable ignored) { }
    }
}
