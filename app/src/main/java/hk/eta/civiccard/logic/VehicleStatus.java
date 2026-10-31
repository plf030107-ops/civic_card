package hk.eta.civiccard.logic;

/**
 * 乘趣「XPlan」车况状态模型。
 * 字段与 assets/vehicleInfoRules.json 中的 key 一一对应。
 */
public final class VehicleStatus {

    // 车门 0=关 1=开
    public int frontLeftDoor, frontRightDoor, rearLeftDoor, rearRightDoor;
    // 门锁 1=已锁
    public int frontLeftLock, frontRightLock, rearLeftLock, rearRightLock;
    // 车窗 0=关 1=开
    public int frontLeftWindow, frontRightWindow, rearLeftWindow, rearRightWindow;

    public int trunk;          // 尾门 0=关
    public int hood;           // 引擎盖
    public int power;          // 2 byte

    public double voltage;                     // V,  x*0.1
    public int remainLMileage;                 // km, x*12
    public double totalMileage;                // km, x*0.1
    public int continuationMileage;            // 续航
    public int batteryPercentage;              // %
    public int smallBattery;                   // 小电瓶

    public double temperatureInside;           // ℃, x*0.5-40
    public double temperatureOutside;          // ℃, x*0.5-40

    public int acSwitch, acAutoSwitch;
    public double acLeftTemp, acRightTemp;     // x*0.5
    public int maxWindLevel, currentWindLevel;

    public int positionP;                      // P 挡
    public int vehicleMode;
    public int remoteMode;
    public int burglarAlarm;                   // 防盗报警

    public long timestampMs;

    public boolean doorsClosed() {
        return frontLeftDoor == 0 && frontRightDoor == 0
                && rearLeftDoor == 0 && rearRightDoor == 0;
    }

    public boolean doorsLocked() {
        return frontLeftLock == 1 && frontRightLock == 1
                && rearLeftLock == 1 && rearRightLock == 1;
    }

    public boolean trunkClosed() {
        return trunk == 0;
    }

    public boolean windowsClosed() {
        return frontLeftWindow == 0 && frontRightWindow == 0
                && rearLeftWindow == 0 && rearRightWindow == 0;
    }

    /**
     * 复现 common_precondition.json 的前置校验。
     * 返回 null 表示可执行，否则返回 App 原话的拒绝原因。
     */
    public String preconditionBlockReason(String alias) {
        if (alias == null) return null;
        switch (alias) {
            case "unlock":
            case "lock":
                if (!doorsClosed()) return "关闭车门";
                if (!trunkClosed()) return "关闭尾门";
                return null;
            case "startEngine":
            case "stopEngine":
                if (!doorsClosed()) return "关闭车门";
                if (!trunkClosed()) return "关闭尾门";
                if (!doorsLocked()) return "车辆上锁";
                return null;
            default:
                return null;
        }
    }
}
