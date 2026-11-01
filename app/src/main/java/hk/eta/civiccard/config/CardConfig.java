package hk.eta.civiccard.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡片配置 —— 存本地 SharedPreferences，卡片按此渲染。
 *
 * 设计目标：让用户自由选择卡片显示哪些状态、哪些功能、什么顺序。
 */
public final class CardConfig {

    // ---------------------------------------------------------- 可显示状态

    /** 状态项：key 与乘趣协议字段一致 */
    public static final class StatusItem {
        public final String key;
        public final String label;

        public StatusItem(String key, String label) {
            this.key = key;
            this.label = label;
        }

        @Override
        public String toString() { return label; }
    }

    /** 乘趣支持的 37 个状态项，按分组排列 */
    public static final StatusItem[] ALL_STATUS = {
            // 门锁
            new StatusItem("frontLeftLock", "左前车锁"),
            new StatusItem("frontRightLock", "右前车锁"),
            new StatusItem("rearLeftLock", "左后车锁"),
            new StatusItem("rearRightLock", "右后车锁"),
            // 车门
            new StatusItem("frontLeftDoor", "左前车门"),
            new StatusItem("frontRightDoor", "右前车门"),
            new StatusItem("rearLeftDoor", "左后车门"),
            new StatusItem("rearRightDoor", "右后车门"),
            // 车窗
            new StatusItem("frontLeftWindow", "左前车窗"),
            new StatusItem("frontRightWindow", "右前车窗"),
            new StatusItem("rearLeftWindow", "左后车窗"),
            new StatusItem("rearRightWindow", "右后车窗"),
            // 车身
            new StatusItem("power", "电源状态"),
            new StatusItem("trunk", "后备箱"),
            new StatusItem("hood", "引擎盖"),
            // 数值
            new StatusItem("voltage", "电瓶电压"),
            new StatusItem("totalMileage", "总里程"),
            new StatusItem("continuationMileage", "剩余续航"),
            new StatusItem("remainLMileage", "续航里程"),
            new StatusItem("batteryPercentage", "电量百分比"),
            new StatusItem("smallBattery", "小电瓶"),
            // 温度
            new StatusItem("temperatureInside", "车内温度"),
            new StatusItem("temperatureOutside", "车外温度"),
            // 空调
            new StatusItem("acSwitch", "AC开关"),
            new StatusItem("acAutoSwitch", "空调自动"),
            new StatusItem("acLeftTemp", "左空调温度"),
            new StatusItem("acRightTemp", "右空调温度"),
            new StatusItem("currentWindLevel", "当前风速"),
            new StatusItem("maxWindLevel", "最大风速"),
            // 状态
            new StatusItem("positionP", "档位"),
            new StatusItem("vehicleMode", "本地模式"),
            new StatusItem("remoteMode", "远程模式"),
            new StatusItem("burglarAlarm", "设防状态"),
    };

    // ---------------------------------------------------------- 可绑定功能

    /** 功能项：alias 与乘趣指令别名一致；null 表示本地动作 */
    public static final class FuncItem {
        public final String alias;   // 下发给乘趣的指令；null = 本地动作
        public final String icon;
        public final String label;

        public FuncItem(String alias, String icon, String label) {
            this.alias = alias;
            this.icon = icon;
            this.label = label;
        }

        @Override
        public String toString() { return label; }
    }

    public static final FuncItem FUNC_NONE    = new FuncItem(null, "", "（空）");
    public static final FuncItem FUNC_REFRESH = new FuncItem("__refresh__", "⟳", "刷新车况");
    public static final FuncItem FUNC_UNLOCK  = new FuncItem("unlock", "🔓", "解锁");
    public static final FuncItem FUNC_LOCK    = new FuncItem("lock", "🔒", "锁车");
    public static final FuncItem FUNC_TRUNK_O = new FuncItem("openTrunk", "🧳", "开后备箱");
    public static final FuncItem FUNC_TRUNK_C = new FuncItem("closeTrunk", "🧳", "关后备箱");
    public static final FuncItem FUNC_WIN_O   = new FuncItem("openWindow", "🪟", "开车窗");
    public static final FuncItem FUNC_WIN_C   = new FuncItem("closeWindow", "🪟", "关车窗");
    public static final FuncItem FUNC_ENGINE_O= new FuncItem("startEngine", "🔑", "远程启动");
    public static final FuncItem FUNC_ENGINE_C= new FuncItem("stopEngine", "🛑", "熄火");
    public static final FuncItem FUNC_SEARCH  = new FuncItem("search", "📍", "寻车");

    /** 全部可选功能（供下拉框） */
    public static final FuncItem[] ALL_FUNCS = {
            FUNC_NONE, FUNC_UNLOCK, FUNC_LOCK,
            FUNC_TRUNK_O, FUNC_TRUNK_C,
            FUNC_WIN_O, FUNC_WIN_C,
            FUNC_ENGINE_O, FUNC_ENGINE_C,
            FUNC_SEARCH, FUNC_REFRESH,
    };

    // ---------------------------------------------------------- 配置字段

    /** 卡片显示的状态项 key，最多 MAX_STATUS 个 */
    public static final int MAX_STATUS = 5;
    /** 卡片功能槽位：2 行 × 2 列 = 4 个（背屏空间有限） */
    public static final int SLOT_COUNT = 4;
    /** 长按防误触时长选项（毫秒） */
    public static final long[] LONG_PRESS_OPTIONS = {0, 500, 1000, 1500, 2000};
    public static final String[] LONG_PRESS_LABELS = {"关闭", "0.5 秒", "1 秒", "1.5 秒", "2 秒"};

    private CardConfig() {}

    /** 默认状态：电压、总里程、剩余续航 */
    public static List<String> defaultStatusKeys() {
        List<String> l = new ArrayList<>();
        l.add("voltage");
        l.add("totalMileage");
        l.add("continuationMileage");
        return l;
    }

    /** 默认功能：解锁、锁车、寻车、刷新 */
    public static FuncItem[] defaultFuncs() {
        return new FuncItem[]{FUNC_UNLOCK, FUNC_LOCK, FUNC_SEARCH, FUNC_REFRESH};
    }

    /** 按 key 找状态项 */
    public static StatusItem findStatus(String key) {
        for (StatusItem s : ALL_STATUS) {
            if (s.key.equals(key)) return s;
        }
        return null;
    }

    /** 按 label 找功能项（下拉框用 label 匹配） */
    public static FuncItem findFuncByLabel(String label) {
        for (FuncItem f : ALL_FUNCS) {
            if (f.label.equals(label)) return f;
        }
        return FUNC_NONE;
    }
}
