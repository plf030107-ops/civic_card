package hk.eta.civiccard.hook;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * MQTT 消息抓取 —— 门锁等实时状态的真实来源。
 *
 * 远程控车能下达，车必然回报执行结果，这条回报走 MQTT，
 * 不是 HTTP，所以抓 OkHttp 抓不到。
 *
 * 这里挂范围放宽：mqtt 包下所有类的所有「带参数」方法，
 * 只要参数里出现 byte[] 或 String 就记录，避免回调名不匹配漏抓。
 */
final class MqttSniffer {

    private static volatile boolean sInstalled = false;
    private static int sCount = 0;

    private MqttSniffer() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;

        String[] classes = {
                "com.ingeek.android.mqtt.MqttManager",
                "com.ingeek.android.mqtt.MqttClientService",
                "com.ingeek.android.mqtt.MqttService",
                "com.ingeek.android.mqtt.IMqttCallback",
        };

        int total = 0;
        for (String cn : classes) {
            Class<?> c = null;
            try {
                c = XposedHelpers.findClassIfExists(cn, cl);
            } catch (Throwable t) {
                XposedBridge.log("CivicCard MQTT find fail " + cn + ": " + t);
                continue;
            }
            if (c == null) {
                XposedBridge.log("CivicCard MQTT miss " + cn);
                continue;
            }

            int n = 0;
            Method[] methods;
            try {
                methods = c.getDeclaredMethods();
            } catch (Throwable t) {
                XposedBridge.log("CivicCard MQTT decl fail " + cn);
                continue;
            }

            for (Method m : methods) {
                if (m.getParameterTypes().length == 0) continue;
                // 只要参数里可能有消息体就挂
                boolean interesting = false;
                for (Class<?> pt : m.getParameterTypes()) {
                    if (pt == byte[].class || pt == String.class
                            || pt.getName().contains("Mqtt")) {
                        interesting = true;
                        break;
                    }
                }
                if (!interesting) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            scan(param);
                        }
                    });
                    n++;
                } catch (Throwable t) {
                    XposedBridge.log("CivicCard MQTT hook fail "
                            + m.getName() + ": " + t);
                }
            }
            if (n > 0) {
                XposedBridge.log("CivicCard MQTT " + c.getSimpleName() + " x" + n);
            }
            total += n;
        }
        XposedBridge.log("CivicCard MqttSniffer installed total=" + total);
    }

    private static void scan(XC_MethodHook.MethodHookParam param) {
        for (Object a : param.args) {
            if (a == null) continue;
            String s = extract(a);
            if (s != null && s.length() >= 6) {
                handle(s);
                return;
            }
        }
    }

    /** 从任意对象里尽力取出字符串内容 */
    private static String extract(Object a) {
        if (a instanceof String) return (String) a;
        if (a instanceof byte[]) {
            byte[] b = (byte[]) a;
            if (b.length < 6) return null;
            try {
                return new String(b, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Throwable t) {
                return null;
            }
        }
        // MqttMessage.getPayload()
        try {
            Method pay = a.getClass().getMethod("getPayload");
            Object p = pay.invoke(a);
            if (p instanceof byte[]) {
                return new String((byte[]) p, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static void handle(String s) {
        if (s == null || s.isEmpty()) return;
        // 过滤明显无意义的内容
        if (s.length() < 6) return;
        boolean printable = true;
        for (int i = 0; i < Math.min(s.length(), 40); i++) {
            char ch = s.charAt(i);
            if (ch < 0x09 || (ch > 0x0D && ch < 0x20)) { printable = false; break; }
        }
        if (!printable) return;

        sCount++;
        StatusStore.writeFile("mqtt_" + (sCount % 30) + ".txt", s);
        if (sCount <= 5 || sCount % 10 == 0) {
            XposedBridge.log("CivicCard MQTT #" + sCount + " len=" + s.length()
                    + " head=" + s.substring(0, Math.min(80, s.length())));
        }
    }
}
