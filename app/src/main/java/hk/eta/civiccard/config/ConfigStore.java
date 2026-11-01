package hk.eta.civiccard.config;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置读写（SharedPreferences）。卡片和配置页同进程，直接读即可。
 */
public final class ConfigStore {

    private static final String SP = "card_config";

    private static final String K_STATUS = "status_keys";     // 逗号分隔
    private static final String K_FUNC_PREFIX = "func_";      // func_0..3 = label
    private static final String K_LONG_PRESS = "long_press_ms";
    private static final String K_BG = "bg_path";
    private static final String K_MODEL_URL = "model_url";

    private ConfigStore() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------- 状态项

    public static List<String> getStatusKeys(Context c) {
        String s = sp(c).getString(K_STATUS, null);
        if (s == null || s.isEmpty()) {
            return CardConfig.defaultStatusKeys();
        }
        List<String> out = new ArrayList<>();
        for (String k : s.split(",")) {
            if (!k.isEmpty()) out.add(k);
        }
        return out.isEmpty() ? CardConfig.defaultStatusKeys() : out;
    }

    public static void setStatusKeys(Context c, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size() && i < CardConfig.MAX_STATUS; i++) {
            if (i > 0) sb.append(',');
            sb.append(keys.get(i));
        }
        sp(c).edit().putString(K_STATUS, sb.toString()).apply();
    }

    // ---------------------------------------------------------- 功能槽位

    public static CardConfig.FuncItem[] getFuncs(Context c) {
        CardConfig.FuncItem[] out = new CardConfig.FuncItem[CardConfig.SLOT_COUNT];
        CardConfig.FuncItem[] def = CardConfig.defaultFuncs();
        for (int i = 0; i < CardConfig.SLOT_COUNT; i++) {
            String label = sp(c).getString(K_FUNC_PREFIX + i, null);
            out[i] = label == null ? def[i] : CardConfig.findFuncByLabel(label);
        }
        return out;
    }

    public static void setFunc(Context c, int slot, CardConfig.FuncItem f) {
        if (slot < 0 || slot >= CardConfig.SLOT_COUNT) return;
        sp(c).edit().putString(K_FUNC_PREFIX + slot, f.label).apply();
    }

    // ---------------------------------------------------------- 防误触

    public static long getLongPressMs(Context c) {
        return sp(c).getLong(K_LONG_PRESS, 0L);
    }

    public static void setLongPressMs(Context c, long ms) {
        sp(c).edit().putLong(K_LONG_PRESS, ms).apply();
    }

    // ---------------------------------------------------------- 背景

    public static String getBgPath(Context c) {
        return sp(c).getString(K_BG, null);
    }

    public static void setBgPath(Context c, String path) {
        sp(c).edit().putString(K_BG, path).apply();
    }

    public static void resetBg(Context c) {
        sp(c).edit().remove(K_BG).apply();
    }

    // ---------------------------------------------------------- 车模图

    public static String getModelUrl(Context c) {
        return sp(c).getString(K_MODEL_URL, null);
    }

    public static void setModelUrl(Context c, String url) {
        sp(c).edit().putString(K_MODEL_URL, url).apply();
    }
}
