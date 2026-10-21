package com.zwiftjoystick;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemClock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把手机模拟成一个 Zwift Protocol (ZP) 手柄（默认：Zwift Ride，明文）。
 *
 * 注意：Android 上所有 App 共用一张 GATT 表。手机上同时运行别的 BLE 外设 App（如骑行台模拟器）时，
 * 我们会收到所有连接的回调，所以按连接维护会话，只给完成 RideOn 握手的连接发消息，
 * 并且从不主动断开连接（那可能是别的 App 的连接）。
 */
@SuppressLint("MissingPermission")
final class ZwiftPeripheral {

    interface Listener {
        void onLog(String line);

        void onState(String state);

        /** side: 0 左 / 1 右 / 2 双侧；waveId < 0 表示自定义波形。 */
        void onHaptic(int side, boolean loop, int waveId, byte[] custom);
    }

    static final class Config {
        int advType = 0x07;            // 厂商数据 4A 09 <type>；{2,3,6,9} 会让 Zwift 启用加密
        byte[] advExtra = {0x00, 0x00};
        boolean encrypted = false;
        int productId = 8;             // device_binding.xml：8 = Ride，11 = Click，14 = Play
        int manufacturerId = 1;        // 1 = ZWIFT
        String name = "Zwift Ride";
        int verMajor = 2, verMinor = 4;
        int digitalMask = 0xFFFF;      // 16 个数字键
        int analogMask = 0x3;          // LH / RH
        int analogMin = -100, analogMax = 100;
        int battery = 88;
        int reportOpcode = 0x23;       // 0x23 Ride / 0x07 Play / 0x37 Click
    }

    /** 一个 BLE 主机连接上的 ZP 会话。 */
    private static final class Session {
        final BluetoothDevice dev;
        int mtu = 23;
        boolean syncTxIndicate;
        boolean handshake;
        ZpCrypto crypto;

        Session(BluetoothDevice dev) {
            this.dev = dev;
        }
    }

    // ---- UUID ----
    static final UUID SVC = UUID.fromString("00000001-19CA-4651-86E5-FA29DCDD09D1");
    static final UUID CH_ASYNC = UUID.fromString("00000002-19CA-4651-86E5-FA29DCDD09D1");
    static final UUID CH_SYNC_RX = UUID.fromString("00000003-19CA-4651-86E5-FA29DCDD09D1");
    static final UUID CH_SYNC_TX = UUID.fromString("00000004-19CA-4651-86E5-FA29DCDD09D1");
    static final UUID CH_6 = UUID.fromString("00000006-19CA-4651-86E5-FA29DCDD09D1");
    static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
    static final UUID ADV_SVC = UUID.fromString("0000FC82-0000-1000-8000-00805F9B34FB");
    static final UUID BAT_SVC = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB");
    static final UUID BAT_LVL = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB");
    static final int ZWIFT_COMPANY_ID = 0x094A;

    // ---- opcode ----
    static final int OP_GET = 0x00, OP_CONTROLLER_REQUEST = 0x12, OP_BATTERY_NOTIF = 0x19,
            OP_CONTROLLER_NOTIFICATION = 0x23, OP_GAME_STATE = 0x28, OP_GET_RESPONSE = 0x3C, OP_RIDE_ON = 0x52;

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    /**
     * 保活：Zwift 一段时间收不到手柄的任何通知就会在骑行界面提示 "Connection Failure"
     * （连接其实还在，按一下键就消失）。空闲时每秒重发一次当前按键状态，状态没变，Zwift 不会当成新的按键。
     */
    private static final long KEEPALIVE_MS = 1000;
    private long lastInputTx;
    private boolean quietTx;
    private final Runnable keepAlive = new Runnable() {
        @Override
        public void run() {
            if (isConnected() && SystemClock.uptimeMillis() - lastInputTx >= KEEPALIVE_MS) {
                quietTx = true;
                sendInput(1);
                if (cfg.reportOpcode == 0x07) sendInput(0);   // Play 左右手柄各自一条
                quietTx = false;
            }
            main.postDelayed(this, KEEPALIVE_MS);
        }
    };
    private Config cfg = new Config();

    private BluetoothGattServer server;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothAdapter adapter;
    private int nameWaitTicks;
    private BluetoothGattCharacteristic chAsync, chSyncRx, chSyncTx, chBattery;
    private final Map<String, Session> sessions = new LinkedHashMap<>();

    // 当前输入状态
    private int buttonMap = 0xFFFFFFFF;   // 低电平有效
    private final int[] analog = new int[4];

    // GATT notify 一次只能有一个在途
    private final ArrayDeque<Object[]> txQueue = new ArrayDeque<>();
    private boolean txBusy;

    ZwiftPeripheral(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    // ===================================================================== 启停

    void start(Config c) {
        stop();
        cfg = c;
        main.postDelayed(keepAlive, KEEPALIVE_MS);
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            log("蓝牙未开启");
            return;
        }
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            log("此设备不支持 BLE 外设广播");
            return;
        }
        // 扫描响应里的名称取自全机蓝牙名，别的 App（如骑行台模拟器）可能改过它，每次广播前都改回来
        this.adapter = adapter;
        if (cfg.name != null && !cfg.name.isEmpty() && !cfg.name.equals(adapter.getName())) {
            log("手机蓝牙名 " + adapter.getName() + " → " + cfg.name);
            adapter.setName(cfg.name);
        }

        server = bm.openGattServer(ctx, gattCallback);
        BluetoothGattService svc = new BluetoothGattService(SVC, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        chAsync = notifyChar(CH_ASYNC, BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        chSyncRx = new BluetoothGattCharacteristic(CH_SYNC_RX,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        chSyncTx = notifyChar(CH_SYNC_TX, BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        BluetoothGattCharacteristic ch6 = notifyChar(CH_6, BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_WRITE);
        svc.addCharacteristic(chAsync);
        svc.addCharacteristic(chSyncRx);
        svc.addCharacteristic(chSyncTx);
        svc.addCharacteristic(ch6);
        server.addService(svc); // 电池服务在 onServiceAdded 里再加
        state("GATT 服务已创建");
    }

    void stop() {
        main.removeCallbacks(keepAlive);
        if (advertiser != null) {
            try {
                advertiser.stopAdvertising(advCallback);
            } catch (Exception ignored) {
            }
        }
        // 只关闭自己的 GATT 服务，不 cancelConnection：同一条链路可能还承载着别的 App 的服务
        if (server != null) server.close();
        server = null;
        sessions.clear();
        txQueue.clear();
        txBusy = false;
    }

    private BluetoothGattCharacteristic notifyChar(UUID uuid, int props) {
        BluetoothGattCharacteristic c = new BluetoothGattCharacteristic(uuid, props,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        c.addDescriptor(new BluetoothGattDescriptor(CCCD,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        return c;
    }

    private void startAdvertising() {
        if (server == null) return;
        // setName() 是异步的：等改名生效后再广播，否则扫描响应里还是旧名字
        if (cfg.name != null && !cfg.name.isEmpty() && !cfg.name.equals(adapter.getName())) {
            if (nameWaitTicks++ < 30) {
                main.postDelayed(this::startAdvertising, 100);
                return;
            }
            log("警告: 3 秒内蓝牙名未变为 " + cfg.name + "（当前 " + adapter.getName() + "），仍开始广播");
        }
        nameWaitTicks = 0;
        byte[] mfr = new byte[1 + cfg.advExtra.length];
        mfr[0] = (byte) cfg.advType;
        System.arraycopy(cfg.advExtra, 0, mfr, 1, cfg.advExtra.length);
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();
        // 主包（≤31B）：flags(3) + ZP 服务数据(19) + 厂商数据(7) = 29B
        // ZP 服务数据 = 128-bit UUID 00000001-19CA-… 后跟能力类型列表：
        // 02 = 用户输入。Zwift 在发现阶段据此创建 ZP_UserInputComponent，设备才会出现在"控制器"配对列表里。
        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceData(new ParcelUuid(SVC), new byte[]{0x02})
                .addManufacturerData(ZWIFT_COMPANY_ID, mfr)
                .setIncludeDeviceName(false)
                .build();
        // 扫描响应：FC82 + 设备名
        AdvertiseData scan = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(ADV_SVC))
                .setIncludeDeviceName(true)
                .build();
        advertiser.startAdvertising(settings, data, scan, advCallback);
        log("广播: " + adapter.getName() + ", ZP 服务数据[02], FC82, 厂商数据 4A 09 " + Hex.of(mfr) + (cfg.encrypted ? "（加密模式）" : "（明文）"));
    }

    private final AdvertiseCallback advCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            state("广播中，等待 Zwift 连接…");
        }

        @Override
        public void onStartFailure(int errorCode) {
            state("广播失败 code=" + errorCode);
        }
    };

    private Session session(BluetoothDevice d) {
        Session s = sessions.get(d.getAddress());
        if (s == null) {
            s = new Session(d);
            sessions.put(d.getAddress(), s);
        }
        return s;
    }

    // ===================================================================== GATT 回调（Binder 线程 → 转主线程）

    private final BluetoothGattServerCallback gattCallback = new BluetoothGattServerCallback() {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            if (server == null) return;
            if (SVC.equals(service.getUuid())) {
                BluetoothGattService bat = new BluetoothGattService(BAT_SVC, BluetoothGattService.SERVICE_TYPE_PRIMARY);
                chBattery = notifyChar(BAT_LVL, BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY);
                bat.addCharacteristic(chBattery);
                server.addService(bat);
            } else {
                main.post(ZwiftPeripheral.this::startAdvertising);
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            main.post(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    session(device);
                    log("链路连接: " + device.getAddress() + "（可能是本 App，也可能是同机其它 BLE App 的连接）");
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Session s = sessions.remove(device.getAddress());
                    log("链路断开: " + device.getAddress() + (s != null && s.handshake ? "（ZP 会话结束）" : ""));
                    txQueue.removeIf(item -> item[0] == s);
                    txBusy = false;
                    pumpTx();
                    state(isConnected() ? "已连接 Zwift" : "广播中，等待 Zwift 连接…");
                }
            });
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int newMtu) {
            main.post(() -> {
                session(device).mtu = newMtu;
                log("MTU(" + device.getAddress() + ") = " + newMtu);
            });
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic c) {
            byte[] v = BAT_LVL.equals(c.getUuid()) ? new byte[]{(byte) cfg.battery} : new byte[0];
            if (server != null) server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, v);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic c,
                                                 boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (responseNeeded && server != null) server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            final byte[] v = value.clone();
            final UUID u = c.getUuid();
            main.post(() -> {
                if (CH_SYNC_RX.equals(u)) onSyncRx(session(device), v);
                else log("写 " + u.toString().substring(0, 8) + ": " + Hex.of(v));
            });
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor d) {
            if (server != null) server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{0, 0});
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor d,
                                             boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (responseNeeded && server != null) server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            final UUID ch = d.getCharacteristic().getUuid();
            final boolean indicate = value != null && value.length > 0 && (value[0] & 2) != 0;
            main.post(() -> {
                if (CH_SYNC_TX.equals(ch)) session(device).syncTxIndicate = indicate;
                log("订阅 " + ch.toString().substring(0, 8) + " = " + (value == null ? "null" : Hex.of(value)));
            });
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            main.post(() -> {
                txBusy = false;
                pumpTx();
            });
        }
    };

    // ===================================================================== 发送

    private void sendRaw(Session s, BluetoothGattCharacteristic c, byte[] data) {
        if (server == null) return;
        if (data.length > s.mtu - 3) log("警告: 帧长 " + data.length + " > MTU-3 (" + (s.mtu - 3) + ")，会被截断");
        txQueue.add(new Object[]{s, c, data});
        pumpTx();
    }

    @SuppressWarnings("deprecation")
    private void pumpTx() {
        if (txBusy || txQueue.isEmpty() || server == null) return;
        Object[] item = txQueue.poll();
        Session s = (Session) item[0];
        BluetoothGattCharacteristic c = (BluetoothGattCharacteristic) item[1];
        byte[] data = (byte[]) item[2];
        c.setValue(data);
        txBusy = server.notifyCharacteristicChanged(s.dev, c, c == chSyncTx && s.syncTxIndicate);
        if (!txBusy) main.postDelayed(this::pumpTx, 20);
    }

    /** 发 ZP 消息：明文 [op][payload] 或加密帧。 */
    private void sendZp(Session s, BluetoothGattCharacteristic c, int opcode, byte[] payload) {
        byte[] plain = new byte[1 + payload.length];
        plain[0] = (byte) opcode;
        System.arraycopy(payload, 0, plain, 1, payload.length);
        byte[] frame = plain;
        if (s.crypto != null && s.crypto.ready() && opcode != OP_RIDE_ON) {
            try {
                frame = s.crypto.encrypt(plain);
            } catch (Exception e) {
                log("加密失败: " + e);
                return;
            }
        }
        if (!quietTx) log("TX " + opName(opcode) + " " + Hex.of(plain));
        sendRaw(s, c, frame);
    }

    // ===================================================================== 接收

    private void onSyncRx(Session s, byte[] raw) {
        byte[] msg = raw;
        // RideOn 握手始终明文；握手后的加密设备消息需要解密
        boolean isRideOn = raw.length >= 6 && raw[0] == 'R' && raw[1] == 'i' && raw[2] == 'd' && raw[3] == 'e';
        if (s.crypto != null && s.crypto.ready() && !isRideOn) {
            try {
                msg = s.crypto.decrypt(raw);
            } catch (Exception e) {
                log("解密失败: " + e + " raw=" + Hex.of(raw));
                return;
            }
        }
        if (msg.length == 0) return;
        int op = msg[0] & 0xFF;
        byte[] payload = Arrays.copyOfRange(msg, 1, msg.length);
        if (op != OP_RIDE_ON) log("RX " + opName(op) + " " + Hex.of(msg) + "  " + Pb.dump(payload));

        switch (op) {
            case OP_RIDE_ON:
                onRideOn(s, payload);
                break;
            case OP_GET:
                onGet(s, (int) Pb.getLong(Pb.parse(payload), 1, -1));
                break;
            case OP_CONTROLLER_REQUEST:
                onControllerRequest(payload);
                break;
            case OP_GAME_STATE:
                onGameState(payload);
                break;
            case 0x04:
            case 0x3F:
                log("  ⚠ Zwift 在这条连接上发了骑行台控制指令（opcode " + opName(op)
                        + "）：它把这条连接当成了 ZP 骑行台，通常是因为在 Zwift 里选了同机其它 App 的设备");
                break;
            default:
                break;
        }
    }

    private void onRideOn(Session s, byte[] p) {
        String head = new String(p, 0, Math.min(5, p.length), StandardCharsets.US_ASCII);
        if (!"ideOn".equals(head)) {
            log("非法 RideOn: " + Hex.of(p));
            return;
        }
        String ver = p.length >= 7 ? (p[5] & 0xFF) + "." + (p[6] & 0xFF) : "?";
        log("RX RideOn 握手（" + s.dev.getAddress() + "），Zwift 协议版本 " + ver
                + (p.length > 7 ? "，附带公钥 " + (p.length - 7) + "B" : ""));

        byte[] reply;
        if (cfg.encrypted) {
            if (p.length < 7 + 64) {
                log("加密模式但 Zwift 没发公钥（该广播类型在 Zwift 侧是明文？）");
                return;
            }
            try {
                s.crypto = new ZpCrypto();
                s.crypto.derive(Arrays.copyOfRange(p, 7, 7 + 64));
            } catch (Exception e) {
                log("ECDH/HKDF 失败: " + e);
                return;
            }
            byte[] pub = s.crypto.publicKey64();
            reply = new byte[8 + 64];
            System.arraycopy(pub, 0, reply, 8, 64);
            log("已派生 AES-256-CCM 会话密钥");
        } else {
            reply = new byte[8];
        }
        System.arraycopy("RideOn".getBytes(StandardCharsets.US_ASCII), 0, reply, 0, 6);
        reply[6] = (byte) cfg.verMajor;
        reply[7] = (byte) cfg.verMinor;
        log("TX RideOn 应答 " + Hex.of(reply, 0, 8) + (reply.length > 8 ? " …" : ""));
        sendRaw(s, chSyncTx, reply);
        s.handshake = true;
        state("握手完成，等待 Zwift 读取设备信息");
    }

    private void onGet(Session s, int objectId) {
        byte[] data;
        switch (objectId) {
            case 0x0000:
                data = devInfoStatus(s);
                state("Zwift 正在读取设备信息…");
                break;
            case 0x0400:
                data = inputConfig();
                state("已连接 Zwift，可以开始操作");
                break;
            case 0x0303:
                data = batteryStatus();
                break;
            case 0x0010:
            case 0x0011:
                data = new Pb.Writer().varint(1, 0).toByteArray();
                break;
            default:
                data = new byte[0];
                break;
        }
        Pb.Writer w = new Pb.Writer().varint(1, objectId);
        if (data.length > 0) w.bytes(2, data);
        log(String.format("GET DO 0x%04X → 应答 %dB", objectId, data.length));
        sendZp(s, chSyncTx, OP_GET_RESPONSE, w.toByteArray());
    }

    /** DO 0x0000 = Zp_DevInfoStatus { 1: Zp_DevInfo } */
    private byte[] devInfoStatus(Session s) {
        Pb.Writer info = new Pb.Writer()
                .varint(1, (cfg.verMajor << 8) | cfg.verMinor)                     // protocol_version
                .bytes(2, new byte[]{0x01, 0x00, 0x02, 0x01})                       // fw 1.2.1 (patch LE16, minor, major)
                .bytes(3, ascii(cfg.name, 12))                                       // device_name
                .bytes(7, ascii("A", 5))                                             // hw_version
                .message(8, new Pb.Writer().varint(1, 2).varint(2, 3))               // capability: 用户输入
                .varint(9, cfg.manufacturerId)
                .varint(10, cfg.productId);
        byte[] full = info.toByteArray();
        // 可选：序列号(15B 定长) / UID(12B 定长)。MTU 足够才带上，避免帧被截断
        if (1 + 4 + 2 + full.length + 2 + 17 + 14 <= s.mtu - 3) {
            info.bytes(6, fixed("ZJ0000000000001", 15)).bytes(11, fixed("ZJOYSTICK001", 12));
        }
        return new Pb.Writer().message(1, info).toByteArray();
    }

    /** DO 0x0400 = Zp_InputConfig */
    private byte[] inputConfig() {
        Pb.Writer w = new Pb.Writer().varint(1, cfg.digitalMask & 0xFFFFFFFFL).varint(2, cfg.analogMask);
        for (int loc = 0; loc < 4; loc++) {
            if ((cfg.analogMask & (1 << loc)) == 0) continue;
            w.message(3, new Pb.Writer().varint(1, loc).sint(2, cfg.analogMin).sint(3, cfg.analogMax));
        }
        return w.toByteArray();
    }

    /** DO 0x0303：结构同 opcode 0x1A {u32, u8 电量?, u16, u16}（全部 required） */
    private byte[] batteryStatus() {
        return new Pb.Writer().varint(1, 0).varint(2, cfg.battery).varint(3, 0).varint(4, 0).toByteArray();
    }

    private void onControllerRequest(byte[] p) {
        Map<Integer, List<Object>> req = Pb.parse(p);
        byte[] haptic = Pb.getBytes(req, 2);
        if (haptic == null) {
            log("  ControllerRequest(非震动): " + Pb.dump(p));
            return;
        }
        Map<Integer, List<Object>> hr = Pb.parse(haptic);
        byte[] play = Pb.getBytes(hr, 1);
        if (play == null) {
            log("  HapticRequest.f2 = " + Pb.dump(haptic) + "（疑似停止/模式）");
            return;
        }
        Map<Integer, List<Object>> h = Pb.parse(play);
        int side = (int) Pb.getLong(h, 1, 2);
        boolean loop = Pb.getLong(h, 2, 0) != 0;
        int wave = (int) Pb.getLong(h, 3, -1);
        byte[] custom = Pb.getBytes(h, 4);
        String sideS = side == 0 ? "左" : side == 1 ? "右" : "双侧";
        String ev = wave >= 0 ? hapticName(wave) : "自定义波形 " + (custom == null ? "" : Hex.of(custom));
        log("  ★ 震动事件: " + sideS + (loop ? " 循环 " : " ") + (wave >= 0 ? "wave=" + wave + " " : "") + ev);
        listener.onHaptic(side, loop, wave, custom);
    }

    private void onGameState(byte[] p) {
        Map<Integer, List<Object>> m = Pb.parse(p);
        byte[] f1 = Pb.getBytes(m, 1);
        if (f1 != null) log("  GameState.f1.value = " + Pb.getLong(Pb.parse(f1), 1, -1));
    }

    // ===================================================================== 输入

    /** keycode 0..15（ZwiftButtonMap），pressed=true 按下；其它值忽略 */
    void setButton(int keycode, boolean pressed) {
        if (keycode < 0 || keycode > 15) return;
        int before = buttonMap;
        if (pressed) buttonMap &= ~(1 << keycode);
        else buttonMap |= (1 << keycode);
        if (before != buttonMap) sendInput(sideOfKey(keycode));
    }

    /** location 0=LH 1=RH 2=LV 3=RV，value -100..100；其它 location 忽略 */
    void setAnalog(int location, int value) {
        if (location < 0 || location > 3 || analog[location] == value) return;
        analog[location] = value;
        sendInput((location & 1) == 0 ? 1 : 0);
    }

    boolean isConnected() {
        for (Session s : sessions.values()) if (s.handshake) return true;
        return false;
    }

    /** 1 = 左手柄（D-pad、LS*、LF、LH/LV），0 = 右手柄 */
    private static int sideOfKey(int k) {
        return (k <= 3 || (k >= 8 && k <= 11)) ? 1 : 0;
    }

    private boolean down(int keycode) {
        return (buttonMap & (1 << keycode)) == 0;
    }

    private void sendInput(int side) {
        int op;
        byte[] payload;
        switch (cfg.reportOpcode) {
            case 0x07: {
                // PlayKeyPadStatus：0 = 按下，1 = 松开；side 1 = 左, 0 = 右
                boolean left = side == 1;
                int[] keys = left ? new int[]{1, 0, 2, 3, 8, 11} : new int[]{6, 7, 4, 5, 12, 15};
                Pb.Writer w = new Pb.Writer().bool(1, left);
                for (int i = 0; i < keys.length; i++) w.varint(2 + i, down(keys[i]) ? 0 : 1);
                w.sint(8, analog[left ? 0 : 1]).sint(9, analog[left ? 2 : 3]);
                op = 0x07;
                payload = w.toByteArray();
                break;
            }
            case 0x37:
                // ClickKeyPadStatus：1 = ＋(RS1)，2 = －(LS1)，0 = 按下
                op = 0x37;
                payload = new Pb.Writer().varint(1, down(12) ? 0 : 1).varint(2, down(8) ? 0 : 1).toByteArray();
                break;
            default: {
                Pb.Writer w = new Pb.Writer().varint(1, buttonMap & 0xFFFFFFFFL);
                for (int loc = 0; loc < 4; loc++) {
                    if ((cfg.analogMask & (1 << loc)) == 0) continue;
                    w.message(3, new Pb.Writer().varint(1, loc).sint(2, analog[loc]));
                }
                op = OP_CONTROLLER_NOTIFICATION;
                payload = w.toByteArray();
                break;
            }
        }
        lastInputTx = SystemClock.uptimeMillis();
        for (Session s : sessions.values()) {
            if (s.handshake) sendZp(s, chAsync, op, payload);
        }
    }

    void sendBattery() {
        byte[] payload = new Pb.Writer().varint(2, cfg.battery).toByteArray();
        for (Session s : sessions.values()) {
            if (s.handshake) sendZp(s, chAsync, OP_BATTERY_NOTIF, payload);
        }
    }

    String inputSummary() {
        return String.format("buttonMap=0x%08X LH=%d RH=%d", buttonMap, analog[0], analog[1]);
    }

    // ===================================================================== 工具

    private static byte[] ascii(String s, int max) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        return b.length > max ? Arrays.copyOf(b, max) : b;
    }

    private static byte[] fixed(String s, int len) {
        return Arrays.copyOf(s.getBytes(StandardCharsets.US_ASCII), len);
    }

    private void log(String s) {
        listener.onLog(s);
    }

    private void state(String s) {
        main.post(() -> listener.onState(s));
    }

    static String opName(int op) {
        switch (op) {
            case 0x00: return "GET";
            case 0x01: return "DEV_INFO_STATUS";
            case 0x04: return "TRAINER_CONFIG_SET";
            case 0x07: return "PLAY_NOTIF";
            case 0x08: return "DFU_START";
            case 0x12: return "CONTROLLER_REQUEST";
            case 0x19: return "BATTERY_NOTIF";
            case 0x23: return "CONTROLLER_NOTIFICATION";
            case 0x28: return "GAME_STATE";
            case 0x2D: return "PLAY_SETTINGS_SET";
            case 0x37: return "CLICK_NOTIF";
            case 0x3C: return "GET_RESPONSE";
            case 0x3E: return "STATUS_RESPONSE";
            case 0x3F: return "SET";
            case 0x41: return "LOG_LEVEL_SET";
            case 0x43: return "GAME_STATE_NOTIFICATION";
            case 0x52: return "RIDE_ON";
            case 0xFF: return "VENDOR";
            default: return String.format("OP_%02X", op);
        }
    }

    static String hapticName(int id) {
        switch (id) {
            case 25: return "COLLISION（碰撞）";
            case 41: return "ROAD_EXTENT（压路边）";
            case 59: return "RETURN_RIDEON（回 RideOn）";
            case 60: return "BOMB_RIDEON（RideOn 炸弹）";
            case 32: return "SHIFT_EXTENT（档位到头）";
            case 72: return "CONNECTED（已连接）";
            case 87: return "SPEED_BOOST（加速）";
            case 89: return "USE_POWERUP（使用道具）";
            case 71: return "HAZARD_SNOW/DIRT（雪地/泥地）";
            case 94: return "HAZARD_GENERAL（路面危险）";
            default: return "未知事件";
        }
    }
}
