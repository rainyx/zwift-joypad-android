package com.zwiftjoystick;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** 全局持有 BLE 外设、设置和日志，Activity 切换不影响连接。 */
public class App extends Application implements ZwiftPeripheral.Listener {

    private static App instance;

    ZwiftPeripheral peripheral;
    Prefs prefs;
    private final StringBuilder log = new StringBuilder();
    private String state = "未启动";
    private boolean running;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<ZwiftPeripheral.Listener> listeners = new CopyOnWriteArrayList<>();
    private final SimpleDateFormat ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    static App get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = new Prefs(this);
        peripheral = new ZwiftPeripheral(this, this);
    }

    void startAdvertising() {
        peripheral.start(prefs.toConfig());
        running = true;
    }

    void stopAdvertising() {
        peripheral.stop();
        running = false;
        onState("已停止");
    }

    boolean isRunning() {
        return running;
    }

    String state() {
        return state;
    }

    synchronized String logText() {
        return log.toString();
    }

    synchronized void clearLog() {
        log.setLength(0);
    }

    void addListener(ZwiftPeripheral.Listener l) {
        listeners.add(l);
    }

    void removeListener(ZwiftPeripheral.Listener l) {
        listeners.remove(l);
    }

    // ---- ZwiftPeripheral.Listener：记录后转发到当前界面 ----

    @Override
    public void onLog(String line) {
        android.util.Log.i("ZwiftJoystick", line);
        String s = ts.format(new Date()) + "  " + line;
        synchronized (this) {
            log.append(s).append('\n');
            if (log.length() > 200_000) log.delete(0, log.length() - 150_000);
        }
        main.post(() -> {
            for (ZwiftPeripheral.Listener l : listeners) l.onLog(s);
        });
    }

    @Override
    public void onState(String s) {
        main.post(() -> {
            state = s;
            for (ZwiftPeripheral.Listener l : listeners) l.onState(s);
        });
    }

    @Override
    public void onHaptic(int side, boolean loop, int waveId, byte[] custom) {
        main.post(() -> {
            for (ZwiftPeripheral.Listener l : listeners) l.onHaptic(side, loop, waveId, custom);
        });
    }
}
