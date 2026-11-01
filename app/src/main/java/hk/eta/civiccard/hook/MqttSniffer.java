package hk.eta.civiccard.hook;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * MQTT 消息抓取 —— 门锁等实时状态的真实来源。
 *
 * 远程控车能下达，车必然回报执行结果；这条回报走 MQTT。
 *
 * 关键类（dexdump 确认实现 IMqttCallback）：
 *   com.ingeek.android.mqtt.MqttManager
 *   com.ingeek.android.mqtt.MqttClientService
 *   com.ingeek.android.mqtt.hivemq.HiveMQClientService
 *   com.ingeek.nokey.component.mqtt.a     ← 业务处理类，最可能是车况落点
 *
 * 只挂具体类的方法，跳过抽象接口（hook 抽象方法会抛 IllegalArgumentException）。
 */
final class MqttSniffer {

    private static volatile boolean sInstalled = false;
    private static int sCount = 0;

    /** 具体实现类（不含接口） */
    private static final String[] CLASSES = {
            "com.ingeek.nokey.component.mqtt.a",
            "com.ingeek.android.mqtt.MqttManager",
            "com.ingeek.android.mqtt.MqttClientService",
            "com.ingeek.android.mqtt.hivemq.HiveMQClientService",
    };

    private MqttSniffer() {}

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;

        int total = 0;
        for (String cn : CLASSES) {
            Class<?> c = null;
            try {
                c = XposedHelpers.findClassIfExists(cn, cl);
            } catch (Throwable ignored) { }
            if (c == null) continue;

            int n = 0;
            Method[] methods;
            try {
                methods = c.getDeclaredMethods();
            } catch (Throwable t) {
                continue;
            }

            for (Method m : methods) {
                // 跳过抽象方法
                if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                if (m.getParameterTypes().length == 0) continue;

                boolean interesting = false;
                for (Class<?> pt : m.getParameterTypes()) {
                    if (pt == byte[].class || pt == String.class
                            || pt.getName().toLowerCase().contains("mqtt")) {
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
                } catch (Throwable ignored) { }
            }
            if (n > 0) {
                XposedBridge.log("CivicCard MQTT " + cn + " x" + n);
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
        if (s == null || s.length() < 6) return;
        boolean printable = true;
        for (int i = 0; i < Math.min(s.length(), 40); i++) {
            char ch = s.charAt(i);
            if (ch < 0x09 || (ch > 0x0D && ch < 0x20)) { printable = false; break; }
        }
        if (!printable) return;

        sCount++;
        StatusStore.writeFile("mqtt_" + (sCount % 30) + ".txt", s);
        if (sCount <= 8 || sCount % 10 == 0) {
            XposedBridge.log("CivicCard MQTT #" + sCount + " len=" + s.length()
                    + " head=" + s.substring(0, Math.min(100, s.length())));
        }
    }
}
