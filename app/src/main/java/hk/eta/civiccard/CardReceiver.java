package hk.eta.civiccard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

import hk.eta.civiccard.config.ConfigStore;
import hk.eta.civiccard.logic.BridgeReader;
import hk.eta.civiccard.logic.CommandSender;
import hk.eta.civiccard.logic.VehicleStatus;

/**
 * 背屏卡片（MAML）与本应用的桥。
 *
 * 卡片 → App：hk.eta.civiccard.NATIVE_CONTROL，extra command
 *             UNLOCK / LOCK / TRUNK / AIR / SEARCH / REFRESH
 * App → 卡片：hk.eta.civiccard.NATIVE_STATUS，extra data（JSON）
 */
public class CardReceiver extends BroadcastReceiver {

    public static final String ACTION_CONTROL = "hk.eta.civiccard.NATIVE_CONTROL";
    public static final String ACTION_STATUS  = "hk.eta.civiccard.NATIVE_STATUS";

    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!ACTION_CONTROL.equals(intent.getAction())) return;

        String cmd = intent.getStringExtra("command");
        if (cmd == null) return;

        switch (cmd) {
            case "UNLOCK":  CommandSender.send(CommandSender.CMD_UNLOCK); break;
            case "LOCK":    CommandSender.send(CommandSender.CMD_LOCK); break;
            case "TRUNK":   CommandSender.send(CommandSender.CMD_OPEN_TRUNK); break;
            case "SEARCH":  CommandSender.send(CommandSender.CMD_SEARCH); break;
            case "AIR":     break;   // 乘趣无独立空调指令，留空
            case "REFRESH": default: break;
        }
        pushStatus(context);
    }

    /** 把当前车况 + 用户所选五个状态打包广播给卡片 */
    public static void pushStatus(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            final String json = buildStatusJson(app);
            sHandler.post(() -> {
                Intent i = new Intent(ACTION_STATUS);
                i.putExtra("data", json);
                app.sendBroadcast(i);
            });
        }).start();
    }

    private static String buildStatusJson(Context ctx) {
        VehicleStatus st = BridgeReader.readStatus();
        JSONObject o = new JSONObject();
        try {
            if (st == null) {
                o.put("status", "打开乘趣刷新车况");
                o.put("model", "我的思域");
                o.put("range", "--");
                o.put("voltage", "--");
                o.put("mileage", "--");
                o.put("updated", "--");
            } else {
                o.put("status", "车况已更新");
                o.put("model", "我的思域");
                o.put("range", st.hasRemainMileage
                        ? String.valueOf(st.continuationMileage) : "--");
                o.put("voltage", st.hasVoltage
                        ? String.format(Locale.US, "%.1f", st.voltage) : "--");
                o.put("mileage", st.hasTotalMileage
                        ? String.valueOf((int) st.totalMileage) : "--");
                o.put("updated", ageText(BridgeReader.statusAgeMs()));
            }

            // 五个状态位：从配置取，最多 5 项
            List<String> keys = ConfigStore.getStatusKeys(ctx);
            for (int i = 0; i < 5; i++) {
                String key = i < keys.size() ? keys.get(i) : null;
                o.put("s" + (i + 1) + "_label", labelOf(key));
                o.put("s" + (i + 1) + "_text", valueOf(st, key));
                o.put("s" + (i + 1) + "_level", levelOf(st, key));
            }
        } catch (Throwable t) {
            // 构造失败时保持默认值
        }
        return o.toString();
    }

    private static String labelOf(String key) {
        if (key == null) return "";
        switch (key) {
            case "voltage": return "电压";
            case "totalMileage": return "总里程";
            case "continuationMileage": return "续航";
            case "remainLMileage": return "续航";
            case "batteryPercentage": return "电量";
            case "temperatureInside": return "车内温";
            case "temperatureOutside": return "车外温";
            case "trunk": return "后备箱";
            case "hood": return "引擎盖";
            case "frontLeftLock": case "frontRightLock":
            case "rearLeftLock": case "rearRightLock": return "车锁";
            case "frontLeftDoor": case "frontRightDoor":
            case "rearLeftDoor": case "rearRightDoor": return "车门";
            case "frontLeftWindow": case "frontRightWindow":
            case "rearLeftWindow": case "rearRightWindow": return "车窗";
            case "positionP": return "档位";
            case "power": return "电源";
            default: return key;
        }
    }

    private static String valueOf(VehicleStatus st, String key) {
        if (key == null) return "";
        if (st == null) return "--";
        switch (key) {
            case "voltage":
                return st.hasVoltage ? String.format(Locale.US, "%.1f", st.voltage) : "--";
            case "totalMileage":
                return st.hasTotalMileage ? String.valueOf((int) st.totalMileage) : "--";
            case "continuationMileage":
                return st.hasRemainMileage ? String.valueOf(st.continuationMileage) : "--";
            case "remainLMileage":
                return String.valueOf(st.remainLMileage);
            case "batteryPercentage":
                return st.hasBattery ? st.batteryPercentage + "%" : "--";
            case "temperatureInside":
                return st.hasTempInside
                        ? String.format(Locale.US, "%.0f°", st.temperatureInside) : "--";
            case "temperatureOutside":
                return String.format(Locale.US, "%.0f°", st.temperatureOutside);
            case "trunk":
                return st.hasTrunk ? (st.trunk == 1 ? "开" : "关") : "--";
            case "hood":
                return st.hood == 1 ? "开" : "关";
            case "positionP":
                return st.positionP == 1 ? "非P" : "P";
            case "power":
                return String.valueOf(st.power);
            default:
                return "--";
        }
    }

    /** good / bad / unknown，供卡片配色 */
    private static String levelOf(VehicleStatus st, String key) {
        if (key == null || st == null) return "unknown";
        switch (key) {
            case "voltage":
                if (!st.hasVoltage) return "unknown";
                return st.voltage >= 11.8 ? "good" : "bad";
            case "trunk":
                if (!st.hasTrunk) return "unknown";
                return st.trunk == 0 ? "good" : "bad";
            case "hood":
                return st.hood == 0 ? "good" : "bad";
            case "frontLeftLock": case "frontRightLock":
            case "rearLeftLock": case "rearRightLock":
                if (!st.hasDoorLock) return "unknown";
                return st.doorsLocked() ? "good" : "bad";
            case "frontLeftDoor": case "frontRightDoor":
            case "rearLeftDoor": case "rearRightDoor":
                if (!st.hasDoor) return "unknown";
                return st.doorsClosed() ? "good" : "bad";
            case "frontLeftWindow": case "frontRightWindow":
            case "rearLeftWindow": case "rearRightWindow":
                if (!st.hasWindow) return "unknown";
                return st.windowsClosed() ? "good" : "bad";
            default:
                return "good";
        }
    }

    private static String ageText(long ageMs) {
        if (ageMs == Long.MAX_VALUE || ageMs < 0) return "--";
        long sec = ageMs / 1000L;
        if (sec < 60) return "刚刚";
        if (sec < 3600) return (sec / 60) + " 分钟前";
        if (sec < 86400) return (sec / 3600) + " 小时前";
        return (sec / 86400) + " 天前";
    }
}
