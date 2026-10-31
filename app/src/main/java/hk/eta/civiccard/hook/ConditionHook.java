package hk.eta.civiccard.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 车况最终来源：hook XKeyApiExtend.applyVehicleCondition 的回调。
 *
 * 这条链路是 App「打开时刷新一次」的自动驾驶路径：
 *   applyVehicleCondition → 云端 getVehicleState → 解析 pstateData
 *   → 得到 VehicleConditionInfo → 回调 onReceived
 *
 * 我们不去解 pstateData 私有编码，而是在回调里直接拿解析好的对象字段。
 * 天然满足「只在打开乘趣时刷新」，不产生额外流量或车辆唤醒。
 */
final class ConditionHook {

    private static volatile boolean sInstalled = false;
    private static long sLastWrite = 0;
    private static String sLastSig = "";

    private ConditionHook() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;

        // 1. 先挂 VehicleConditionInfo 的字段读取（任意 getter 触发）
        try {
            Class<?> c = XposedHelpers.findClassIfExists(
                    "com.ingeek.nokey.component.sdk.VehicleConditionInfo", cl);
            if (c != null) {
                XposedBridge.log("CivicCard ConditionHook fields="
                        + c.getDeclaredFields().length);
            }
        } catch (Throwable ignored) { }

        // 2. 挂 applyVehicleCondition 所在类/回调
        hookApply(cl);
    }

    private static void hookApply(ClassLoader cl) {
        // XKeyApiExtend 可能在多个类里，逐个尝试
        String[] classes = {
                "com.ingeek.nokeeu.key.xplan.XKeyApiExtend",
                "com.ingeek.nokeeu.key.XKeyApiExtend",
        };
        for (String cn : classes) {
            try {
                Class<?> c = XposedHelpers.findClassIfExists(cn, cl);
                if (c == null) continue;
                int n = 0;
                for (Method m : c.getDeclaredMethods()) {
                    if (!m.getName().toLowerCase().contains("vehiclecondition")) continue;
                    try {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                // 回调对象可能作为参数传入
                                for (Object a : param.args) {
                                    tryDump(a);
                                }
                                Object r = param.getResult();
                                tryDump(r);
                            }
                        });
                        n++;
                    } catch (Throwable ignored) { }
                }
                XposedBridge.log("CivicCard ConditionHook " + cn + " x" + n);
            } catch (Throwable t) {
                XposedBridge.log("CivicCard ConditionHook " + cn + " fail " + t);
            }
        }
    }

    /** 尝试从任意对象里挖出 VehicleConditionInfo */
    private static void tryDump(Object obj) {
        if (obj == null) return;
        try {
            Class<?> cls = obj.getClass();
            String name = cls.getName();
            if (name.contains("VehicleConditionInfo")) {
                dump(obj);
                return;
            }
            // 回调接口：遍历其字段找 VehicleConditionInfo
            for (Field f : cls.getDeclaredFields()) {
                if (!f.getType().getName().contains("VehicleConditionInfo")) continue;
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) dump(v);
            }
        } catch (Throwable ignored) { }
    }

    private static void dump(Object cond) {
        long now = System.currentTimeMillis();
        if (now - sLastWrite < 500) return;

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ts\":").append(now);

        putB(sb, cond, "frontLeftDoor");
        putB(sb, cond, "frontRightDoor");
        putB(sb, cond, "rearLeftDoor");
        putB(sb, cond, "rearRightDoor");
        putB(sb, cond, "frontLeftLock");
        putB(sb, cond, "frontRightLock");
        putB(sb, cond, "rearLeftLock");
        putB(sb, cond, "rearRightLock");
        putB(sb, cond, "frontLeftWindow");
        putB(sb, cond, "frontRightWindow");
        putB(sb, cond, "rearLeftWindow");
        putB(sb, cond, "rearRightWindow");
        putB(sb, cond, "trunk");
        putB(sb, cond, "hood");

        putN(sb, cond, "continuationMileage", "continuationMileage");
        putN(sb, cond, "totalMileage", "totalMileage");
        putN(sb, cond, "voltage", "voltage");
        putN(sb, cond, "batteryPercentage", "batteryPercentage");
        putN(sb, cond, "temperatureInside", "temperatureInside");
        putN(sb, cond, "temperatureOutside", "temperatureOutside");
        putN(sb, cond, "power", "power");

        sb.append('}');
        String json = sb.toString();

        if (json.equals(sLastSig)) return;
        sLastSig = json;
        sLastWrite = now;

        BridgeServer.get().onConditionInfo(json);
        XposedBridge.log("CivicCard COND " + json);
    }

    private static void putB(StringBuilder sb, Object o, String n) {
        try {
            Field f = o.getClass().getDeclaredField(n);
            f.setAccessible(true);
            Object v = f.get(o);
            if (v instanceof Boolean) {
                sb.append(",\"").append(n).append("\":").append(((Boolean) v) ? 1 : 0);
            }
        } catch (Throwable ignored) { }
    }

    private static void putN(StringBuilder sb, Object o, String n, String out) {
        try {
            Field f = o.getClass().getDeclaredField(n);
            f.setAccessible(true);
            Object v = f.get(o);
            double d = -9999;
            if (v instanceof Number) d = ((Number) v).doubleValue();
            else if (v instanceof Boolean) d = ((Boolean) v) ? 1 : 0;
            if (d != -9999) {
                sb.append(",\"").append(out).append("\":").append(d);
            }
        } catch (Throwable ignored) { }
    }
}
