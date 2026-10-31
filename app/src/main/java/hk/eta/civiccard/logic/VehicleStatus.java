package hk.eta.civiccard.logic;

/**
 * 车况模型。
 *
 * 关键：用 hasXxx 标记区分「字段存在且值为 0」和「字段根本没有」。
 * 云端 pstateData 目前只解析出电压/总里程/剩余里程，
 * 门锁门窗字段缺失时不能当成「已锁车/全关」。
 */
public final class VehicleStatus {

    public int frontLeftDoor, frontRightDoor, rearLeftDoor, rearRightDoor;
    public int frontLeftLock, frontRightLock, rearLeftLock, rearRightLock;
    public int frontLeftWindow, frontRightWindow, rearLeftWindow, rearRightWindow;

    public int trunk;
    public int hood;
    public int power;

    public double voltage;
    public int remainLMileage;
    public double totalMileage;
    public int continuationMileage;
    public int batteryPercentage;
    public int smallBattery;

    public double temperatureInside;
    public double temperatureOutside;

    public int acSwitch, acAutoSwitch;
    public double acLeftTemp, acRightTemp;
    public int maxWindLevel, currentWindLevel;

    public int positionP;
    public int vehicleMode;
    public int remoteMode;
    public int burglarAlarm;

    public long timestampMs;

    // ---- 字段是否存在 ----
    public boolean hasDoorLock;
    public boolean hasDoor;
    public boolean hasWindow;
    public boolean hasTrunk;
    public boolean hasVoltage;
    public boolean hasTotalMileage;
    public boolean hasRemainMileage;
    public boolean hasBattery;
    public boolean hasTempInside;

    public boolean doorsClosed() {
        return hasDoor && frontLeftDoor == 0 && frontRightDoor == 0
                && rearLeftDoor == 0 && rearRightDoor == 0;
    }

    public boolean doorsLocked() {
        return hasDoorLock && frontLeftLock == 1 && frontRightLock == 1
                && rearLeftLock == 1 && rearRightLock == 1;
    }

    public boolean trunkClosed() {
        return hasTrunk && trunk == 0;
    }

    public boolean windowsClosed() {
        return hasWindow && frontLeftWindow == 0 && frontRightWindow == 0
                && rearLeftWindow == 0 && rearRightWindow == 0;
    }

    /** 复现乘趣 common_precondition 的前置校验；未知状态时放行 */
    public String preconditionBlockReason(String alias) {
        if (alias == null) return null;
        switch (alias) {
            case "unlock":
            case "lock":
                if (hasDoor && !doorsClosed()) return "关闭车门";
                if (hasTrunk && !trunkClosed()) return "关闭尾门";
                return null;
            case "startEngine":
            case "stopEngine":
                if (hasDoor && !doorsClosed()) return "关闭车门";
                if (hasTrunk && !trunkClosed()) return "关闭尾门";
                if (hasDoorLock && !doorsLocked()) return "车辆上锁";
                return null;
            default:
                return null;
        }
    }
}
