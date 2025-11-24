package com.example.findingbunks_part2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import android.content.Context;

public class ObdManager {

    private static final String TAG = "ObdManager";
    private final String DEVICE_NAME_TO_CONNECT = "OBDII";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Context context;
    private Handler uiHandler;
    private ObdDataListener listener;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice obdDevice;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    private boolean isConnected = false;

    public interface ObdDataListener {
        void onObdDataReceived(String speed, float fuelLeft, float estimatedRange);
        void onObdStatusChanged(String status);
    }

    public ObdManager(Context context, ObdDataListener listener) {
        this.context = context;
        this.listener = listener;
        this.uiHandler = new Handler(Looper.getMainLooper());
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Main connect method with proper checks
     */
    public void connect() {
        // Check 1: Bluetooth supported?
        if (bluetoothAdapter == null) {
            notifyUI("Bluetooth not supported on this device");
            return;
        }

        // Check 2: Bluetooth enabled?
        if (!bluetoothAdapter.isEnabled()) {
            notifyUI("Please enable Bluetooth first");
            return;
        }

        // Check 3: Permissions granted?
        if (!checkBluetoothPermissions()) {
            notifyUI("Bluetooth permissions not granted");
            return;
        }

        // Check 4: Find device if not already found
        if (obdDevice == null) {
            findObdDevice();
            if (obdDevice == null) {
                notifyUI("OBD device not found. Please pair it in Bluetooth settings.");
                return;
            }
        }

        // All checks passed - start connection
        Log.i(TAG, "Starting connection to OBD device: " + obdDevice.getName());
        listener.onObdStatusChanged("Connecting...");
        connectThread = new ConnectThread(obdDevice);
        connectThread.start();
    }

    public void disconnect() {
        Log.i(TAG, "Disconnecting from OBD device...");
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        isConnected = false;
        listener.onObdStatusChanged("Disconnected");
    }

    /**
     * Check if Bluetooth permissions are granted
     */
    private boolean checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 11 and below
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Find the OBD device from paired devices
     */
    @SuppressLint("MissingPermission")
    private void findObdDevice() {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "Cannot search for devices - permissions not granted");
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices == null || pairedDevices.isEmpty()) {
            Log.w(TAG, "No paired devices found.");
            return;
        }

        for (BluetoothDevice device : pairedDevices) {
            String deviceName = device.getName();
            Log.d(TAG, "Checking paired device: " + deviceName);

            if (deviceName != null && deviceName.toUpperCase().contains("OBD")) {
                obdDevice = device;
                Log.i(TAG, "✅ Found OBD device: " + deviceName + " [" + device.getAddress() + "]");
                return;
            }
        }

        Log.e(TAG, "❌ No OBD device found in paired devices. Please pair your OBD dongle first.");
    }

    /**
     * Get the MAC address of the connected OBD device
     */
    @SuppressLint("MissingPermission")
    public String getObdMacAddress() {
        if (obdDevice != null && checkBluetoothPermissions()) {
            return obdDevice.getAddress();
        }
        return null;
    }

    /**
     * Helper to notify UI with Toast and listener
     */
    private void notifyUI(String message) {
        uiHandler.post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            listener.onObdStatusChanged(message);
        });
    }

    // ==================== CONNECTION THREAD ====================

    private class ConnectThread extends Thread {
        private final BluetoothDevice device;
        private BluetoothSocket socket;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
        }

        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            try {
                Log.i(TAG, "Creating RFCOMM socket...");
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);

                if (bluetoothAdapter.isDiscovering()) {
                    Log.d(TAG, "Canceling discovery...");
                    bluetoothAdapter.cancelDiscovery();
                }

                Log.i(TAG, "Attempting to connect to OBD...");
                socket.connect(); // This is the blocking call

                isConnected = true;
                Log.i(TAG, "✅ Successfully connected to OBD!");

                uiHandler.post(() -> listener.onObdStatusChanged("Connected"));

                // Start the data thread
                connectedThread = new ConnectedThread(socket);
                connectedThread.start();

            } catch (IOException e) {
                Log.e(TAG, "❌ Connection failed: " + e.getMessage(), e);
                uiHandler.post(() -> {
                    listener.onObdStatusChanged("Connection Failed: " + e.getMessage());
                    Toast.makeText(context, "Connection failed. Check if OBD is powered on.", Toast.LENGTH_LONG).show();
                });
                cancel();
            }
        }

        public void cancel() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignored) {}
        }
    }

    // ==================== CONNECTED THREAD ====================

    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inStream;
        private final OutputStream outStream;
        private boolean shouldStop = false;

        private float fuelUsedLiters = 0f;
        private long lastFuelUpdateTimeMillis = 0;
        private final float tankCapacityLiters = 37.0f;
        private static final float AVERAGE_MILEAGE_KML = 20.0f;

        public ConnectedThread(BluetoothSocket socket) throws IOException {
            this.socket = socket;
            this.inStream = socket.getInputStream();
            this.outStream = socket.getOutputStream();
        }

        @Override
        public void run() {
            Log.i(TAG, "Starting OBD initialization...");

            if (!initializeOBD()) {
                Log.e(TAG, "OBD initialization failed");
                uiHandler.post(() -> listener.onObdStatusChanged("OBD Init Failed"));
                return;
            }

            Log.i(TAG, "✅ OBD initialized successfully. Starting data loop...");
            lastFuelUpdateTimeMillis = System.currentTimeMillis();

            while (!shouldStop && isConnected) {
                try {
                    String mafResponse = executeCommand("0110\r");
                    String speedResponse = executeCommand("010D\r");

                    float mafValue = parseMAF(mafResponse);
                    int speedValue = parseSpeed(speedResponse);

                    if (mafValue > 0) {
                        long now = System.currentTimeMillis();
                        float elapsedSec = (now - lastFuelUpdateTimeMillis) / 1000f;
                        lastFuelUpdateTimeMillis = now;
                        float fuelRateLph = (mafValue * 3600) / (14.5f * 0.832f * 1000f);
                        fuelUsedLiters += (fuelRateLph / 3600f) * elapsedSec;
                    }

                    float currentFuelLeft = tankCapacityLiters - fuelUsedLiters;
                    float currentRange = currentFuelLeft * AVERAGE_MILEAGE_KML;
                    String speedStr = speedValue >= 0 ? speedValue + " km/h" : "N/A";

                    uiHandler.post(() -> listener.onObdDataReceived(speedStr, currentFuelLeft, currentRange));

                    Thread.sleep(2000);

                } catch (Exception e) {
                    Log.e(TAG, "Error in OBD data loop: " + e.getMessage());
                    if (!shouldStop) {
                        uiHandler.post(() -> listener.onObdStatusChanged("Data read error"));
                    }
                }
            }

            Log.i(TAG, "OBD data loop ended");
        }

        private boolean initializeOBD() {
            try {
                Log.d(TAG, "Sending ATZ (reset)...");
                executeCommand("ATZ\r");
                Thread.sleep(1500);

                Log.d(TAG, "Sending ATE0 (echo off)...");
                executeCommand("ATE0\r");
                Thread.sleep(500);

                Log.d(TAG, "Sending ATL0 (linefeeds off)...");
                executeCommand("ATL0\r");
                Thread.sleep(500);

                Log.d(TAG, "Sending ATSP0 (auto protocol)...");
                executeCommand("ATSP0\r");
                Thread.sleep(500);

                return true;
            } catch (Exception e) {
                Log.e(TAG, "Initialization failed: " + e.getMessage());
                return false;
            }
        }

        private String executeCommand(String cmd) throws IOException, InterruptedException {
            outStream.write(cmd.getBytes());
            outStream.flush();

            byte[] buffer = new byte[1024];
            StringBuilder result = new StringBuilder();
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < 2000) {
                if (inStream.available() > 0) {
                    int readBytes = inStream.read(buffer);
                    result.append(new String(buffer, 0, readBytes));
                    if (result.toString().contains(">")) break;
                }
                Thread.sleep(50);
            }

            return result.toString().replaceAll("[\r\n >]", "").trim();
        }

        private float parseMAF(String response) {
            try {
                if (!response.startsWith("4110")) return -1;
                String hexA = response.substring(4, 6);
                String hexB = response.substring(6, 8);
                return (((Integer.parseInt(hexA, 16) * 256) + Integer.parseInt(hexB, 16))) / 100f;
            } catch (Exception e) {
                return -1;
            }
        }

        private int parseSpeed(String response) {
            try {
                if (!response.startsWith("410D")) return -1;
                return Integer.parseInt(response.substring(4, 6), 16);
            } catch (Exception e) {
                return -1;
            }
        }

        public void cancel() {
            shouldStop = true;
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
        }
    }
}