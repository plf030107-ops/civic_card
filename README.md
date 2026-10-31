# 乘趣背屏卡片（十代思域）

把乘趣（`com.ingeek.nokey`）的车况和控车投射到小米 17 Pro Max 背屏，
**不改动乘趣本体**，可随时切回系统原生背屏。

## 实测得到的硬事实

全部来自本机 5.0.0（versionCode 77410）的静态分析与运行验证，不是推测。

### 乘趣装在哪

- 包名 `com.ingeek.nokey`，uid **u0_a378（10378）**
- 数据目录 `/data/user/0/com.ingeek.nokey`，**在主空间用户 0**
- 注意：在受限 mount namespace 里 `ls` 会误报 No such file，需 `nsenter -t 1 -m`

### 车控入口（dexdump 精确确认）

```
com.ingeek.nokeeu.key.xplan.XKeyApi
  sendVehicleCommand(IngeekVehicleCommand)
  sendVehicleCommand(IngeekVehicleCommand, VehicleCommandSendListener)
  connectVehicle / disconnectVehicle / getVehicleConnectionStatus
  hasKey / login / logout / setVehicleCommandListener
  sendCustomData / sendVehicleTransmission / getLocalKeyId
```

**指令对象构造**（`IngeekVehicleCommand` 字段全 private，只能走 Builder）：

```java
IngeekVehicleCommand.Builder
  public Builder()
  public IngeekVehicleCommand create()      ← 是 create()，不是 build()
  public Builder setName(String)            ← 是 setName()，不是 commandName()
  public Builder setTimeout(int)
  public Builder setVin(String)
  public Builder setData(byte[])
  public Builder initWithCode(int)
  public Builder initWithData(byte[])
  public Builder initWithTag(byte)
```

`IngeekVehicleCommand` 只有三个 public synthetic 构造器和 native 方法 `s(Object,Object)`，
无法直接 new。

**车况帧来源**（`VehicleCommandListener`）：

```java
boolean isEnableCommandRawDataReturn()
boolean isEnableCommandRawDataReturn(String)
void onExecuteFail(IngeekVehicleCommandResponse, IngeekException)
void onExecuteSuccess(IngeekVehicleCommandResponse)
void onExecuting(IngeekVehicleCommandResponse)
void onReceiveCommandRawData(String, byte[], IngeekException)   ← 车况帧在这
void onReceiveCommandRawData(byte[], IngeekException)           ← 车况帧在这
```

**响应对象**（`IngeekVehicleCommandResponse`）：
`getCommandCode()` / `getCommandTag()` / `getCommandValue()` / `getReason()` / `getResult()` / `getVin()`


### 9 条指令（`assets/command_timeout_config.json`）

| alias | BLE 超时 | 远程超时 |
|---|---|---|
| lock | 3000ms | 4000ms |
| unlock | 3000ms | 4000ms |
| openWindow | 3000ms | 4000ms |
| closeWindow | 3000ms | 4000ms |
| search（寻车） | 3000ms | 4000ms |
| openTrunk | 3000ms | 4000ms |
| closeTrunk | 3000ms | 4000ms |
| startEngine | 3000ms | 4000ms |
| stopEngine | 3000ms | 4000ms |

十代思域的无感蓝牙走的就是 BLE 这条，本地就能发完，不碰云端鉴权。

### 车况协议（`assets/vehicleInfoRules.json`）

**关键：字段是位域，不是字节对齐。** 这点我一开始搞错了，
`length` 单位是 bit，多个字段共用一个字节。

```json
"meta": {
  "version": 1, "length": 216, "minLength": 136,
  "headerLength": 56, "littleEndian": 0
}
```

`littleEndian: 0` → **大端，位序 MSB first**。56 字节头 + 20 字节载荷 = 76 字节。

| 位偏移 | 宽 | 字段 | 载荷字节 | 换算 |
|---|---|---|---|---|
| 0 | 1 | frontLeftDoor | 0 bit0 | 0关1开 |
| 1 | 1 | frontRightDoor | 0 bit1 | |
| 2 | 1 | rearLeftDoor | 0 bit2 | |
| 3 | 1 | rearRightDoor | 0 bit3 | |
| 4 | 1 | frontLeftLock | 0 bit4 | 1已锁 |
| 5 | 1 | frontRightLock | 0 bit5 | |
| 6 | 1 | rearLeftLock | 0 bit6 | |
| 7 | 1 | rearRightLock | 0 bit7 | |
| 8 | 2 | power | 1 | |
| 10 | 1 | frontLeftWindow | 1 | 0关1开 |
| 11 | 1 | frontRightWindow | 1 | |
| 12 | 1 | rearLeftWindow | 1 | |
| 13 | 1 | rearRightWindow | 1 | |
| 14 | 1 | trunk | 1 | 0关 |
| 15 | 1 | hood | 1 | |
| 16 | 8 | temperatureInside | 2 | x*0.5-40 |
| 24 | 8 | temperatureOutside | 3 | x*0.5-40 |
| 32 | 8 | voltage | 4 | x*0.1 |
| 40 | 8 | remainLMileage | 5 | **x*12** |
| 48 | 32 | totalMileage | 6 | x*0.1 |
| 80 | 4 | maxWindLevel | 10 | |
| 84 | 4 | currentWindLevel | 10 | |
| 88 | 8 | acRightTemp | 11 | x*0.5 |
| 96 | 8 | acLeftTemp | 12 | x*0.5 |
| 104 | 1 | acSwitch | 13 | |
| 105 | 1 | acAutoSwitch | 13 | |
| 106 | 3 | acRsv | 13 | |
| 109 | 1 | acAutoCycle | 13 | |
| 110 | 1 | acInternalCycle | 13 | |
| 111 | 1 | acExternalCycle | 13 | |
| 112 | 1 | positionP | 14 | P挡 |
| 113 | 1 | vehicleMode | 14 | |
| 114 | 2 | remoteMode | 14 | |
| 116 | 4 | burglarAlarm | 14 | |
| 120 | 16 | continuationMileage | 15 | |
| 136 | 16 | batteryPercentage | 17 | |
| 152 | 8 | smallBattery | 19 | |

合计 **160 bit = 20 字节**，与 `headerLength=56` 精确吻合。

`tools/check_offsets.sh` 可从 JSON 原文重新推导这张表核对，
`tools/bitparse.sh <hex>` 可对任意帧做解析验证。


### 前置校验（`assets/common_precondition.json`）

`unlock` / `lock` 都要求：

- `doorClose` → 不满足提示「关闭车门」
- `trunkClose` → 不满足提示「关闭尾门」

`startEngine` / `stopEngine` 额外要求 `lock` 通过（「车辆上锁」）。

**卡片发指令前必须跑这个判断**，否则车端直接拒绝。已在 `VehicleStatus.preconditionBlockReason()` 里实现。

## 背屏侧

`hk.uwu.reareye` 1.2.1，AIDL 接口：

```
hk.uwu.reareye.widgetapi.IRearWidgetApiService
  registerBusinessFile(String, String)
  registerBusiness(String, String, String, int, int)
  unregisterBusiness(String, String)
  disableCardDisplay(String, String, String)
  postNotice(String, String, Bundle, Bundle)
```

权限 `hk.uwu.reareye.permission.ACCESS_REAR_WIDGET_API`（已授予）。

## 架构

```
背屏卡片 (hk.eta.civiccard)
   │  写 cmd_req（纯文本别名）
   ▼
/data/local/tmp/civic_card/          ← 双向文件通道，777 权限
   │  status.json / last_result.txt
   ▲
乘趣进程（LSPosed 注入 com.ingeek.nokey）
   ├─ hook XKeyApi.sendVehicleCommand        → 对照 App 自己发的指令
   ├─ hook onReceiveCommandRawData           → 截获车况帧 byte[]
   ├─ hook onExecuting/Success/Fail          → 指令执行状态
   └─ FileObserver 监听 cmd_req              → Builder.create() 反射下发
```

用文件而非 Binder：Xposed 注入的进程无法新增 Manifest 组件，
AIDL Service 声明不出来。文件通道简单可靠，且全程只读乘趣。

## 当前完成度

| 模块 | 状态 |
|---|---|
| 车控 API 逆向 | ✅ dexdump 精确确认 |
| 车况协议表 | ✅ 位域布局，经构造帧端到端验证 |
| 前置校验复现 | ✅ doorClose / trunkClose / lock |
| Hook 层 | ✅ 代码完成，**未实机验证** |
| 卡片 UI | ✅ 完成，976×596 适配 |
| REAREye 注册 | ✅ 声明式 meta-data 路线 |
| 编译产物 | ❌ 设备无 javac/d8，需 CI |

## UI 说明

背屏实测 **976 × 596 px @450dpi**（约 390dp × 238dp），圆角 101px。

布局 `card_main.xml`：

```
┌──────────────────────────┬──────────┐
│ 十代思域          ● 已连接 │  🔓   🔒 │
│                          │          │
│ 🔒 已锁车                 │  🧳   📍 │
│                          │          │
│ 左前  右前  左后  右后     │          │
│  关    关    关    关     │  提示区   │
│ ─────────────────────    │          │
│ 电量   续航   车内        │          │
│ 78%   320km  24°C        │          │
└──────────────────────────┴──────────┘
```

状态语义色：门开=红、窗开=黄、已锁=绿、未锁=黄。

两个界面：
- `SettingsActivity`（桌面图标）— 自检四项：REAREye、权限、桥、车况
- `CardActivity` — 卡片本体，1.5 秒轮询

## 权限与 root

`/data/local/tmp/civic_card` 普通 App 写不了，因此：

- `CommandSender` 优先 `su -c` 写 `cmd_req`
- `BridgeReader` 读不到时退回 `su -c cat`

卡片进程需具备 root 权限。



## 构建

本地没有 javac/d8，走 GitHub Actions：

```bash
git init && git add . && git commit -m init
git remote add origin <你的仓库>
git push -u origin main
```

`.github/workflows/build.yml` 会自动出 APK。

## 激活步骤

1. 安装 APK
2. LSPosed 里启用模块，**作用域勾选 `com.ingeek.nokey`**
3. 强制停止乘趣后重开
4. 验证：`cat /data/local/tmp/civic_card/bridge_ready` 有内容即 hook 成功
5. 车况：`cat /data/local/tmp/civic_card/status.json`
6. 发指令：`echo -n unlock > /data/local/tmp/civic_card/cmd_req`

## 切回原生背屏

REAREye 卡片是叠加在 systemui 上的，关闭模块或从背屏移除卡片即回原生。
乘趣本身完全没动。

## GATT 层实测（车钥匙 iKeyBCB8B）

`snoop` 与 `dumpsys bluetooth_manager` 双向印证：

| 用途 | Handle | ATT 帧 | 说明 |
|---|---|---|---|
| 车况通知 | `0x0035` | `1b 35 00` | ATT Handle Value Notification |
| 指令写入 | `0x0032` | `52 32 00` | ATT Write Command |
| CCCD | `0x0037` | writeDescriptor | 订阅时写 |

一次解锁的完整时间线：

```
19:40:45.391  registerForNotification  handle=0x35  enable=true
19:40:45.392  writeDescriptor          handle=0x37
19:40:45.521  writeCharacteristic      handle=0x32
19:42:27.019  writeCharacteristic      handle=0x32   ← 用户按解锁
```

ATT 帧前缀：`09 02 XX`，`00`=上行指令，`01`/`02`=下行状态。

**注意**：`btsnooz` 格式会把大包拆成 15 字节分片，每片只保留前 6 字节
ATT 头，55/89 字节的真实载荷抓不全。所以 `StatusCodec` 那个手写解析器
只作兜底 —— 实际 hook 拿的是 SDK 已经解析好的 `byte[]`，不需要手搓。

## 已知限制

- 车况依赖乘趣进程存活（BLE 回调触发），进程被杀则停更
- 卡片发指令走 `Builder.setName(alias)`，alias 必须是那 9 个之一
- `unlock`/`lock` 必须过 `doorClose` + `trunkClose` 前置校验

