package hk.eta.civiccard.hook;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * MQTT 消息抓取 —— 门锁等实时状态的真正来源。
 *
 * 为什么之前没找到：
 *   远程控车能用，说明指令能下发到车；车必然要回报执行结果。
 *   这条回报走的是 MQTT（com.ingeek.android.mqtt.MqttManager），
 *   不是 HTTP 接口，所以抓 OkHttp 永远抓不到。
 *
 * 车里种了 Paho 客户端，回调方法名有 onMessageArrived / onMessageReceived 两种可能。
 */
final class MqttSniffer {

    private static volatile boolean sInstalled = false;
    private static int sCount = 0;

    /** 疑似 MQTT 回调所在类 */
    private static final String[] CLASSES = {
            "com.ingeek.android.mqtt.MqttManager",
            "com.ingeek.android.mqtt.MqttClientService",
            "com.ingeek.android.mqtt.IMqttCallback",
    };

    /** 回调方法名候选 */
    private static final String[] METHODS = {
            "onMessageArrived", "onMessageReceived", "messageArrived",
            "onMessage", "onSuccess", "handleMessage",
    };

    private MqttSniffer() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;

        int total = 0;
        for (String cn : CLASSES) {
            try {
                Class<?> c = XposedHelpers.findClassIfExists(cn, cl);
                if (c == null) continue;
                int n = 0;
                for (String mn : METHODS) {
                    try {
                        int k = XposedBridge.hookAllMethods(c, mn, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                dump(param);
                            }
                        }).size();
                        n += k;
                    } catch (Throwable ignored) { }
                }
                // 顺带挂所有参数的 public 方法，防止回调名不在候选里
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getParameterTypes().length == 0) continue;
                    if (isCandidate(m.getName())) continue;
                    try {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                for (Object a : param.args) {
                                    if (a instanceof byte[]) {
                                        byte[] b = (byte[]) a;
                                        if (b.length >= 8) {
                                            handle(new String(b, 0, b.length,
                                                    java.nio.charset.StandardCharsets.UTF_8),
                                                    "byte[]");
                                            return;
                                        }
                                    } else if (a instanceof String) {
                                        String s = (String) a;
                                        if (s.length() >= 8) {
                                            handle(s, "String");
                                            return;
                                        }
                                    }
                                }
                            }
                        });
                        n++;
                    } catch (Throwable ignored) { }
                }
                if (n > 0) XposedBridge.log("CivicCard MqttSniffer " + cn + " x" + n);
                total += n;
            } catch (Throwable t) {
                XposedBridge.log("CivicCard MqttSniffer " + cn + " fail: " + t);
            }
        }
        XposedBridge.log("CivicCard MqttSniffer installed total=" + total);
    }

    private static boolean isCandidate(String name) {
        for (String m : METHODS) if (m.equals(name)) return true;
        return false;
    }

    /** 主回调：参数可能是 MqttMessage（内部有 payload byte[]） */
    private static void dump(XC_MethodHook.MethodHookParam param) {
        for (Object a : param.args) {
            if (a == null) continue;

            // MqttMessage -> payload
            try {
                Class<?> cls = a.getClass();
                Method pay = cls.getMethod("getPayload");
                Object p = pay.invoke(a);
                if (p instanceof byte[]) {
                    byte[] b = (byte[]) p;
                    handle(new String(b, 0, b.length,
                            java.nio.charset.StandardCharsets.UTF_8), "MqttMessage");
                    return;
                }
            } catch (Throwable ignored) { }

            if (a instanceof byte[]) {
                byte[] b = (byte[]) a;
                if (b.length >= 8) {
                    handle(new String(b, 0, b.length,
                            java.nio.charset.StandardCharsets.UTF_8), "byte[]");
                    return;
                }
            }
            if (a instanceof String) {
                String s = (String) a;
                if (s.length() >= 8) {
                    handle(s, "String");
                    return;
                }
            }
        }
    }

    private static void handle(String s, String from) {
        if (s == null || s.isEmpty()) return;
        sCount++;
        StatusStore.writeFile("mqtt_" + (sCount % 20) + ".txt",
                "from=" + from + "\n----\n" + s);
        if (sCount % 5 == 1) {
            XposedBridge.log("CivicCard MQTT #" + sCount + " from=" + from
                    + " len=" + s.length()
                    + " head=" + s.substring(0, Math.min(60, s.length())));
        }
    }
}
