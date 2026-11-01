package hk.eta.civiccard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.util.Locale;

import hk.eta.civiccard.logic.BridgeReader;
import hk.eta.civiccard.logic.CommandSender;
import hk.eta.civiccard.logic.VehicleStatus;
import hk.eta.civiccard.ui.CardActivity;

/**
 * 背屏卡片（MAML）与本应用的桥。
 *
 * 卡片是 MAML 组件，跑在 systemui 进程里，无法直接访问本应⽤的私有数据。
 * 因此通过广播通信：
 *
 *   卡片 → 本应用：action = hk.eta.civiccard.NATIVE_CONTROL
 *                  extra  command = UNLOCK / LOCK / REFRESH
 *
 *   本应用 → 卡片：action = hk.eta.civiccard.NATIVE_STATUS
 *                  extra  data = {"status","model","range","voltage","mileage","updated"}
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
            case "UNLOCK":
                CommandSender.send(CommandSender.CMD_UNLOCK);
                break;
            case "LOCK":
                CommandSender.send(CommandSender.CMD_LOCK);
                break;
            case "REFRESH":
            default:
                break;
        }
        // 无论什么指令，都回推一次当前车况
        pushStatus(context);
    }

    /** 把当前车况打包成 JSON 广播给卡片 */
    public static void pushStatus(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            final String json = buildStatusJson(app);
            sHandler.post(() -> {
                Intent i = new Intent(ACTION_STATUS);
                i.setPackage("hk.uwu.reareye");
                i.putExtra("data", json);
                app.sendBroadcast(i);
                // 同时发给 systemui（MAML 所在进程）
                Intent i2 = new Intent(ACTION_STATUS);
                i2.putExtra("data", json);
                app.sendBroadcast(i2);
            });
        }).start();
    }

    private static String buildStatusJson(Context ctx) {
        VehicleStatus st = BridgeReader.readStatus();
        StringBuilder sb = new StringBuilder("{");
        if (st == null) {
            sb.append("\"status\":\"暂无车况，打开乘趣刷新\",");
            sb.append("\"model\":\"思域\",");
            sb.append("\"range\":\"--\",\"voltage\":\"--\",\"mileage\":\"--\",\"updated\":\"--\"");
            sb.append('}');
            return sb.toString();
        }

        sb.append("\"status\":\"车况已更新\",");
        sb.append("\"model\":\"思域\",");
        sb.append("\"range\":\"").append(st.hasRemainMileage
                ? String.valueOf(st.continuationMileage) : "--").append("\",");
        sb.append("\"voltage\":\"").append(st.hasVoltage
                ? String.format(Locale.US, "%.1f", st.voltage) : "--").append("\",");
        sb.append("\"mileage\":\"").append(st.hasTotalMileage
                ? String.valueOf((int) st.totalMileage) : "--").append("\",");
        sb.append("\"updated\":\"").append(ageText(BridgeReader.statusAgeMs())).append("\"");
        sb.append('}');
        return sb.toString();
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
