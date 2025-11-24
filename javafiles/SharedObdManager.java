package com.example.findingbunks_part2;

import android.Manifest;
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
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class SharedObdManager {

    private static final SharedObdManager instance = new SharedObdManager();
    public static SharedObdManager getInstance() {
        return instance;
    }

    private static final String TAG = "SharedObdManager";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

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
        void onVinReceived(String vin);
    }

    private SharedObdManager() {
        this.uiHandler = new Handler(Looper.getMainLooper());
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void registerListener(ObdDataListener listener) {
        this.listener = listener;
        if (listener != null) {
            listener.onObdStatusChanged(isConnected ? "Connected" : "Disconnected");
        }
    }

    public void unregisterListener(ObdDataListener listener) {
        if (this.listener == listener) {
            this.listener = null;
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Main connect method with proper permission checks
     */
    public void connect(Context context) {
        if (isConnected || connectThread != null) {
            Log.d(TAG, "Already connected or connecting.");
            notifyUI(context, "Already connecting...");
            return;
        }

        if (bluetoothAdapter == null) {
            notifyUI(context, "Bluetooth not supported");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            notifyUI(context, "Please enable Bluetooth first");
            return;
        }

        if (!checkBluetoothPermissions(context)) {
            notifyUI(context, "Bluetooth permissions not granted. Please grant permissions in FetchIdsActivity.");
            return;
        }

        if (obdDevice == null) {
            findObdDevice(context);
            if (obdDevice == null) {
                notifyUI(context, "OBD device not found. Please pair it in Bluetooth settings.");
                return;
            }
        }

        if (checkBluetoothPermissions(context)) {
            try {
                String deviceName = obdDevice.getName();
                String deviceAddress = obdDevice.getAddress();
                Log.i(TAG, "🔌 Starting connection to: " + deviceName + " [" + deviceAddress + "]");
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException getting device info", e);
            }
        }

        if (listener != null) listener.onObdStatusChanged("Connecting...");
        connectThread = new ConnectThread(obdDevice, context);
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
        if (listener != null) listener.onObdStatusChanged("Disconnected");
    }

    /**
     * Check if Bluetooth permissions are granted
     */
    private boolean checkBluetoothPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean connectGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
            boolean scanGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;

            if (!connectGranted || !scanGranted) {
                Log.e(TAG, "Missing Bluetooth permissions on Android 12+");
                return false;
            }
            return true;
        } else {
            boolean bluetoothGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                    == PackageManager.PERMISSION_GRANTED;
            boolean adminGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
                    == PackageManager.PERMISSION_GRANTED;

            if (!bluetoothGranted || !adminGranted) {
                Log.e(TAG, "Missing Bluetooth permissions on Android 11 or lower");
                return false;
            }
            return true;
        }
    }

    /**
     * Find OBD device from paired devices
     */
    public void findObdDevice(Context context) {
        if (!checkBluetoothPermissions(context)) {
            Log.e(TAG, "Cannot search - permissions not granted");
            return;
        }

        if (obdDevice != null) {
            if (checkBluetoothPermissions(context)) {
                try {
                    Log.d(TAG, "OBD device already found: " + obdDevice.getName());
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException accessing device name", e);
                }
            }
            return;
        }

        try {
            // Explicit permission check before getBondedDevices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("BLUETOOTH_CONNECT permission not granted");
                }
            }

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

            if (pairedDevices == null || pairedDevices.isEmpty()) {
                Log.w(TAG, "No paired Bluetooth devices found");
                return;
            }

            Log.d(TAG, "Searching through " + pairedDevices.size() + " paired devices...");

            for (BluetoothDevice device : pairedDevices) {
                try {
                    String deviceName = device.getName();
                    String deviceAddress = device.getAddress();

                    Log.d(TAG, "  Checking: " + deviceName + " [" + deviceAddress + "]");

                    if (deviceName != null && deviceName.toUpperCase().contains("OBD")) {
                        obdDevice = device;
                        Log.i(TAG, "✅ Found OBD device: " + deviceName + " [" + deviceAddress + "]");
                        return;
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException accessing device info", e);
                }
            }

            Log.w(TAG, "❌ No OBD device found. Make sure it's paired in Bluetooth settings.");

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while searching for devices", e);
        }
    }

    /**
     * Get MAC address
     */
    public String getObdMacAddress(Context context) {
        if (!checkBluetoothPermissions(context)) {
            Log.e(TAG, "Cannot get MAC - permissions not granted");
            return null;
        }

        if (obdDevice != null) {
            try {
                // Explicit permission check before getAddress
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                        throw new SecurityException("BLUETOOTH_CONNECT permission not granted");
                    }
                }

                return obdDevice.getAddress();
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException getting MAC address", e);
            }
        }
        return null;
    }

    private void notifyUI(Context context, String message) {
        uiHandler.post(() -> {
            if (context != null) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
            if (listener != null) listener.onObdStatusChanged(message);
        });
    }

    public void fetchVin() {
        if (connectedThread != null && isConnected) {
            connectedThread.fetchVehicleIdentifier();
        }
    }

    // ==================== CONNECTION THREAD ====================
    private class ConnectThread extends Thread {
        private final BluetoothDevice device;
        private BluetoothSocket socket;
        private Context context;

        public ConnectThread(BluetoothDevice device, Context context) {
            this.device = device;
            this.context = context;
        }

        @Override
        public void run() {
            if (!checkBluetoothPermissions(context)) {
                Log.e(TAG, "Connection aborted - missing permissions");
                uiHandler.post(() -> {
                    if (listener != null) listener.onObdStatusChanged("Permission denied");
                });
                return;
            }

            try {
                Log.i(TAG, "📡 Creating RFCOMM socket...");

                // Explicit permission check before createRfcommSocketToServiceRecord
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                        throw new SecurityException("BLUETOOTH_CONNECT permission not granted");
                    }
                }

                socket = device.createRfcommSocketToServiceRecord(MY_UUID);

                if (bluetoothAdapter.isDiscovering()) {
                    Log.d(TAG, "Canceling discovery...");

                    // Explicit permission check before cancelDiscovery
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                                != PackageManager.PERMISSION_GRANTED) {
                            throw new SecurityException("BLUETOOTH_SCAN permission not granted");
                        }
                    }

                    bluetoothAdapter.cancelDiscovery();
                }

                Log.i(TAG, "🔌 Attempting standard connection...");
                socket.connect();

                onConnectionSuccess();

            } catch (IOException e) {
                Log.e(TAG, "⚠️ Standard connection failed: " + e.getMessage());
                Log.i(TAG, "Trying fallback connection method...");

                if (!tryFallbackConnection()) {
                    onConnectionFailure();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "❌ SecurityException during connection", e);
                onConnectionFailure();
            }
        }

        private void onConnectionSuccess() {
            isConnected = true;
            Log.i(TAG, "✅ Successfully connected to OBD!");

            uiHandler.post(() -> {
                if (listener != null) listener.onObdStatusChanged("Connected");
                if (context != null) {
                    Toast.makeText(context, "✅ OBD Connected!", Toast.LENGTH_SHORT).show();
                }
            });

            try {
                connectedThread = new ConnectedThread(socket);
                connectedThread.start();
            } catch (IOException e) {
                Log.e(TAG, "Failed to start data thread", e);
                cancel();
            }
        }

        private void onConnectionFailure() {
            uiHandler.post(() -> {
                if (listener != null) listener.onObdStatusChanged("Connection Failed");
                if (context != null) {
                    Toast.makeText(context,
                            "❌ Connection failed.\n\n" +
                                    "Make sure:\n" +
                                    "• Car ignition is ON (not just ACC)\n" +
                                    "• OBD dongle LED is blinking\n" +
                                    "• No other app is connected to it",
                            Toast.LENGTH_LONG).show();
                }
            });
            cancel();
        }

        private boolean tryFallbackConnection() {
            if (!checkBluetoothPermissions(context)) return false;

            try {
                Log.i(TAG, "🔧 Trying fallback connection method...");

                if (socket != null) {
                    try { socket.close(); } catch (IOException ignored) {}
                }

                // Explicit permission check before reflection call
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                        throw new SecurityException("BLUETOOTH_CONNECT permission not granted");
                    }
                }

                Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                socket = (BluetoothSocket) m.invoke(device, 1);

                socket.connect();

                onConnectionSuccess();
                return true;

            } catch (Exception ex) {
                Log.e(TAG, "❌ Fallback connection also failed", ex);
                return false;
            }
        }

        public void cancel() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignored) {}
            isConnected = false;
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

        private final String[] identifierCommands = {"0902\r", "0904\r", "090A\r", "0100\r"};
        private volatile boolean isFetchingVin = false;

        public ConnectedThread(BluetoothSocket socket) throws IOException {
            this.socket = socket;
            this.inStream = socket.getInputStream();
            this.outStream = socket.getOutputStream();
        }

        @Override
        public void run() {
            Log.i(TAG, "🔧 Starting OBD initialization...");

            if (!initializeOBD()) {
                Log.e(TAG, "❌ OBD initialization failed");
                uiHandler.post(() -> {
                    if(listener != null) listener.onObdStatusChanged("OBD Init Failed");
                });
                return;
            }

            Log.i(TAG, "✅ OBD initialized. Starting data loop...");
            lastFuelUpdateTimeMillis = System.currentTimeMillis();

            while (!shouldStop && isConnected) {
                try {
                    if (isFetchingVin) {
                        Thread.sleep(100);
                        continue;
                    }

                    Thread.sleep(100);
                    String mafResponse = executeCommand("0110\r");
                    float mafValue = parseMAF(mafResponse);

                    Thread.sleep(100);
                    String speedResponse = executeCommand("010D\r");
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

                    uiHandler.post(() -> {
                        if (listener != null) listener.onObdDataReceived(speedStr, currentFuelLeft, currentRange);
                    });

                    Thread.sleep(1500);

                } catch (Exception e) {
                    Log.e(TAG, "Error in OBD data loop: " + e.getMessage());
                    if (!shouldStop) {
                        uiHandler.post(() -> {
                            if(listener != null) listener.onObdStatusChanged("Data read error");
                        });
                    }
                }
            }

            Log.i(TAG, "OBD data loop ended");
        }

        public void fetchVehicleIdentifier() {
            if (isFetchingVin) return;
            isFetchingVin = true;
            Log.d(TAG, "🔍 Starting VIN fetch...");

            new Thread(() -> {
                String carId = null;
                for (String cmd : identifierCommands) {
                    try {
                        Log.d(TAG, "Trying command: " + cmd.trim());

                        Thread.sleep(200);

                        String response = executeCommand(cmd);
                        Log.d(TAG, "Response: " + response);

                        if (cmd.equals("0902\r") && (response.contains("4902") || response.contains("49 02"))) {
                            carId = parseVin(response);
                            Log.i(TAG, "Found VIN: " + carId);
                        } else if (cmd.equals("0904\r") && (response.contains("4904") || response.contains("49 04"))) {
                            carId = parseCalibrationId(response);
                            Log.i(TAG, "Found Calibration ID: " + carId);
                        } else if (cmd.equals("090A\r") && (response.contains("490A") || response.contains("49 0A"))) {
                            carId = parseEcuName(response);
                            Log.i(TAG, "Found ECU Name: " + carId);
                        } else if (cmd.equals("0100\r") && (response.contains("4100") || response.contains("41 00"))) {
                            carId = createPidFingerprint(response);
                            Log.i(TAG, "Created PID Fingerprint: " + carId);
                        }

                        if (carId != null) break;

                    } catch (Exception e) {
                        Log.e(TAG, "Error executing command: " + cmd, e);
                    }
                }

                final String finalCarId = carId;
                uiHandler.post(() -> {
                    if (listener != null) listener.onVinReceived(finalCarId);
                });
                isFetchingVin = false;
                Log.d(TAG, "✅ VIN fetch finished.");
            }).start();
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

                Log.d(TAG, "Testing with 0100 command...");
                String response = executeCommand("0100\r");
                boolean success = response.contains("4100") || !response.contains("ERROR");

                if (success) {
                    Log.i(TAG, "✅ OBD initialization successful!");
                } else {
                    Log.e(TAG, "❌ OBD initialization failed. Response: " + response);
                }

                return success;

            } catch (Exception e) {
                Log.e(TAG, "Initialization failed: " + e.getMessage(), e);
                return false;
            }
        }

        private String executeCommand(String cmd) throws IOException, InterruptedException {
            Log.d(TAG, "Sending command: '" + cmd.trim() + "'");

            // Clear any lingering data in the input stream
            if (inStream.available() > 0) {
                byte[] clearBuffer = new byte[inStream.available()];
                inStream.read(clearBuffer);
                Log.d(TAG, "Cleared " + clearBuffer.length + " bytes from input stream");
            }

            outStream.write(cmd.getBytes());
            outStream.flush();

            StringBuilder result = new StringBuilder();
            long startTime = System.currentTimeMillis();
            long timeout = 3000;

            while (System.currentTimeMillis() - startTime < timeout) {
                if (inStream.available() > 0) {
                    byte[] buffer = new byte[1024];
                    int bytes = inStream.read(buffer);
                    result.append(new String(buffer, 0, bytes));

                    if (result.toString().contains(">")) {
                        break;
                    }
                }
                Thread.sleep(50);
            }

            String finalResult = result.toString().replace(">", "").trim();
            Log.d(TAG, "Received response: '" + finalResult + "'");
            return finalResult;
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

        private String parseVin(String response) {
            if (response == null) return null;
            response = response.replaceAll("\\s", "").toUpperCase();
            int startIndex = response.indexOf("4902");
            if (startIndex == -1) startIndex = response.indexOf("014");
            if (startIndex == -1) return null;
            try {
                String data = response.substring(startIndex + 4).replaceAll("[0-9A-F]:", "").replace(">", "");
                int dataStart = -1;
                for (int i = 0; i < data.length() - 1; i += 2) {
                    int val = Integer.parseInt(data.substring(i, i + 2), 16);
                    if ((val >= 0x30 && val <= 0x39) || (val >= 0x41 && val <= 0x5A)) {
                        dataStart = i;
                        break;
                    }
                }
                if (dataStart == -1) return null;
                String hexData = data.substring(dataStart);
                StringBuilder vin = new StringBuilder();
                for (int i = 0; i < hexData.length(); i += 2) {
                    if (i + 2 <= hexData.length()) {
                        int val = Integer.parseInt(hexData.substring(i, i + 2), 16);
                        if ((val >= 0x30 && val <= 0x39) || (val >= 0x41 && val <= 0x5A)) {
                            char c = (char) val;
                            if (c != 'I' && c != 'O' && c != 'Q') vin.append(c);
                        }
                    }
                }
                String vinStr = vin.toString().trim();
                return vinStr.length() >= 17 ? vinStr.substring(0, 17) : null;
            } catch (Exception e) { return null; }
        }

        private String parseCalibrationId(String response) {
            try {
                response = response.replaceAll("\\s", "").toUpperCase();
                int startIndex = response.indexOf("4904");
                if (startIndex == -1) return null;
                String data = response.substring(startIndex + 4).replace(">", "");
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < Math.min(data.length(), 64); i += 2) {
                    int val = Integer.parseInt(data.substring(i, i + 2), 16);
                    if (val >= 0x20 && val <= 0x7E) result.append((char) val);
                }
                String calId = result.toString().trim();
                return calId.isEmpty() ? null : calId;
            } catch (Exception e) { return null; }
        }

        private String parseEcuName(String response) {
            try {
                response = response.replaceAll("\\s", "").toUpperCase();
                int startIndex = response.indexOf("490A");
                if (startIndex == -1) return null;
                String data = response.substring(startIndex + 4).replace(">", "");
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < Math.min(data.length(), 40); i += 2) {
                    int val = Integer.parseInt(data.substring(i, i + 2), 16);
                    if (val >= 0x20 && val <= 0x7E) result.append((char) val);
                }
                String ecuName = result.toString().trim();
                return ecuName.isEmpty() ? null : ecuName;
            } catch (Exception e) { return null; }
        }

        private String createPidFingerprint(String response) {
            try {
                response = response.replaceAll("\\s", "").toUpperCase();
                int startIndex = response.indexOf("4100");
                if (startIndex == -1) return null;
                String data = response.substring(startIndex + 4, Math.min(startIndex + 20, response.length())).replace(">", "");
                return "PID_" + data;
            } catch (Exception e) { return null; }
        }

        public void cancel() {
            shouldStop = true;
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
        }
    }
}