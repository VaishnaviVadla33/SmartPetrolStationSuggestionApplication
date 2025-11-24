package com.example.findingbunks_part2;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.findingbunks_part2.network.ApiClient;
import com.example.findingbunks_part2.network.ApiService;
import com.example.findingbunks_part2.network.HelloResponse;
import com.example.findingbunks_part2.network.RegisterRequest;
import com.example.findingbunks_part2.network.RegisterResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.example.findingbunks_part2.security.SecurityManager;
import com.example.findingbunks_part2.security.SecureStorage;

public class FetchIdsActivity extends AppCompatActivity {

    private static final String TAG = "FetchIDsApp";
    private static final int PERMISSIONS_REQUEST_CODE = 101;

    // UI Components
    private TextView tvStatus, tvAndroidId, tvDongleMac, tvVin;
    private TextView tvPythonStatus, tvRole;
    private Button btnGetAndroidId, btnGetDongleMac, btnGetVin;
    private Button btnEnterManual, btnModifyIds, btnRegisterBackend, btnProceed;
    private Button btnTestConnection;
    private TextView tvConnectionStatus;

    // Collapsible role details
    private LinearLayout roleDetailsHeader, roleDetailsBox;
    private TextView tvToggleDetails, tvDetailRole, tvDetailMode, tvDetailCarId, tvDetailMac, tvDetailAndroid;

    // Header menu icon
    private ImageView ivMenu;

    // Required permissions
    private String[] requiredPermissions;

    // Current IDs / mode
    private String currentAndroidId = null;
    private String currentMac = null;
    private String currentCarId = null;
    private String currentMode = "manual"; // "manual" / "obd"
    private String currentUserRole = "SECONDARY";

    // Retrofit API
    private ApiService api;

    // Shared OBD Manager
    private SharedObdManager sharedObdManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fetch_ids);

        // Handle window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            View topBar = findViewById(R.id.topBar);
            topBar.setPadding(topBar.getPaddingLeft(), systemBars.top,
                    topBar.getPaddingRight(), topBar.getPaddingBottom());
            return insets;
        });

        // Initialize API client
        api = ApiClient.getClient().create(ApiService.class);

        // Initialize shared OBD manager
        sharedObdManager = SharedObdManager.getInstance();

        // Initialize all views and setup
        initViews();
        initPermissions();
        setupClickListeners();
        healthCheckPython();
        checkAndRequestPermissions();
        displayPhoneAndroidId();
    }

    /**
     * Initialize all UI components
     */
    private void initViews() {
        // TextViews
        tvStatus = findViewById(R.id.tvStatus);
        tvAndroidId = findViewById(R.id.tvAndroidId);
        tvDongleMac = findViewById(R.id.tvDongleMac);
        tvVin = findViewById(R.id.tvVin);
        tvPythonStatus = findViewById(R.id.tvPythonStatus);
        tvRole = findViewById(R.id.tvRole);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);

        // Buttons
        btnGetAndroidId = findViewById(R.id.btnGetAndroidId);
        btnGetDongleMac = findViewById(R.id.btnGetDongleMac);
        btnGetVin = findViewById(R.id.btnGetVin);
        btnEnterManual = findViewById(R.id.btnEnterManual);
        btnModifyIds = findViewById(R.id.btnModifyIds);
        btnRegisterBackend = findViewById(R.id.btnRegisterBackend);
        btnProceed = findViewById(R.id.btnProceed);
        btnTestConnection = findViewById(R.id.btnTestConnection);

        // Header
        ivMenu = findViewById(R.id.ivMenu);

        // Role details (collapsible)
        roleDetailsHeader = findViewById(R.id.roleDetailsHeader);
        roleDetailsBox = findViewById(R.id.roleDetailsBox);
        tvToggleDetails = findViewById(R.id.tvToggleDetails);
        tvDetailRole = findViewById(R.id.tvDetailRole);
        tvDetailMode = findViewById(R.id.tvDetailMode);
        tvDetailCarId = findViewById(R.id.tvDetailCarId);
        tvDetailMac = findViewById(R.id.tvDetailMac);
        tvDetailAndroid = findViewById(R.id.tvDetailAndroid);

        // Initially hide role details
        roleDetailsHeader.setVisibility(View.GONE);
        roleDetailsBox.setVisibility(View.GONE);

        // Setup collapsible toggle
        roleDetailsHeader.setOnClickListener(v -> toggleRoleDetails());
    }

    /**
     * Toggle visibility of role details box
     */
    private void toggleRoleDetails() {
        if (roleDetailsBox.getVisibility() == View.VISIBLE) {
            roleDetailsBox.setVisibility(View.GONE);
            tvToggleDetails.setText("▼ Show Details");
        } else {
            roleDetailsBox.setVisibility(View.VISIBLE);
            tvToggleDetails.setText("▲ Hide Details");
        }
    }

    /**
     * Initialize required permissions based on Android version
     */
    private void initPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions = new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            requiredPermissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
    }

    /**
     * Setup all click listeners
     */
    private void setupClickListeners() {
        // Individual ID fetch buttons
        btnGetAndroidId.setOnClickListener(v -> displayPhoneAndroidId());
        btnGetDongleMac.setOnClickListener(v -> findObdDeviceAndShowMac());
        btnGetVin.setOnClickListener(v -> fetchVinFromObd());

        // Test connection button
        btnTestConnection.setOnClickListener(v -> testBluetoothConnection());

        // Mode selection buttons
        btnEnterManual.setOnClickListener(v -> showManualEntryDialog());
        btnModifyIds.setOnClickListener(v -> showModifyIdsDialog());

        // Backend registration
        btnRegisterBackend.setOnClickListener(v -> registerWithBackend());

        // Menu
        ivMenu.setOnClickListener(this::showPopupMenu);

        // Proceed button
        btnProceed.setOnClickListener(v -> {
            if (sharedObdManager.isConnected()) {
                sharedObdManager.disconnect();
            }
            Intent intent = new Intent(FetchIdsActivity.this, MapsActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Test Bluetooth connection before fetching data
     */
    private void testBluetoothConnection() {
        Log.i(TAG, "========== STARTING BLUETOOTH TEST ==========");
        tvConnectionStatus.setText("Testing connection...");
        tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));

        // Check if Bluetooth is enabled
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            tvConnectionStatus.setText("Bluetooth not supported");
            Log.e(TAG, "❌ Bluetooth adapter is null - device doesn't support Bluetooth");
            return;
        }

        if (!btAdapter.isEnabled()) {
            tvConnectionStatus.setText("Bluetooth is disabled");
            Log.e(TAG, "❌ Bluetooth is not enabled. Please turn on Bluetooth.");
            toast("Please enable Bluetooth first!");
            return;
        }

        Log.i(TAG, "✅ Bluetooth adapter is available and enabled");

        // Check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ BLUETOOTH_CONNECT permission not granted");
                tvConnectionStatus.setText("Permission denied");
                toast("Bluetooth permissions not granted!");
                return;
            }
        }

        Log.i(TAG, "✅ Permissions are granted");

        // List all paired devices for debugging
        try {
            Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
            Log.i(TAG, "📱 Found " + (pairedDevices != null ? pairedDevices.size() : 0) + " paired devices:");
            if (pairedDevices != null) {
                for (BluetoothDevice device : pairedDevices) {
                    Log.i(TAG, "  - " + device.getName() + " [" + device.getAddress() + "]");
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ SecurityException listing devices", e);
        }

        sharedObdManager.registerListener(new SharedObdManager.ObdDataListener() {
            @Override
            public void onObdDataReceived(String speed, float fuelLeft, float estimatedRange) {
                // Not needed for connection test
            }

            @Override
            public void onObdStatusChanged(String status) {
                Log.i(TAG, "📡 OBD Status Changed: " + status);
                tvConnectionStatus.setText("Connection Status: " + status);

                if (status.equals("Connected")) {
                    tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));

                    // Get MAC address
                    String mac = sharedObdManager.getObdMacAddress(FetchIdsActivity.this);
                    if (mac != null) {
                        currentMac = mac;
                        tvDongleMac.setText("Dongle MAC: " + mac);
                        Log.i(TAG, "✅ Got MAC address: " + mac);
                    } else {
                        Log.w(TAG, "⚠️ Could not get MAC address");
                    }

                    // Enable other buttons
                    btnGetVin.setEnabled(true);
                    toast("✅ Connection successful! You can now fetch VIN.");

                } else if (status.contains("Failed") || status.contains("not found")) {
                    tvConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    btnGetVin.setEnabled(false);
                    Log.e(TAG, "❌ Connection failed: " + status);
                }
            }

            @Override
            public void onVinReceived(String vin) {
                if (vin != null) {
                    currentCarId = vin;
                    currentMode = "obd";
                    tvVin.setText("Car ID: " + vin);
                    tvStatus.setText("Status: VIN received successfully");
                    toast("VIN retrieved: " + vin);
                    Log.i(TAG, "✅ VIN received: " + vin);
                } else {
                    tvVin.setText("Car ID: Could not retrieve");
                    tvStatus.setText("Status: VIN fetch failed");
                    toast("Could not retrieve VIN. Try manual entry.");
                    Log.w(TAG, "⚠️ VIN fetch returned null");
                }
            }
        });

        Log.i(TAG, "🔌 Calling sharedObdManager.connect()...");
        sharedObdManager.connect(FetchIdsActivity.this);
    }

    /**
     * Fetch VIN using already-connected OBD
     */
    private void fetchVinFromObd() {
        if (!sharedObdManager.isConnected()) {
            toast("Not connected to OBD. Please test connection first.");
            return;
        }

        tvVin.setText("Car ID: Fetching...");
        tvStatus.setText("Status: Fetching VIN...");
        sharedObdManager.fetchVin();
    }

    /**
     * Find OBD device and show MAC (uses shared manager)
     */
    private void findObdDeviceAndShowMac() {
        tvDongleMac.setText("Dongle MAC: Searching...");
        tvStatus.setText("Status: Searching for OBD dongle...");

        sharedObdManager.findObdDevice(this);
        String mac = sharedObdManager.getObdMacAddress(this);

        if (mac != null) {
            currentMac = mac;
            tvDongleMac.setText("Dongle MAC: " + mac);
            tvStatus.setText("Status: OBD dongle found");
            toast("OBD dongle found!");
        } else {
            tvDongleMac.setText("Dongle MAC: Not Found");
            tvStatus.setText("Status: OBD dongle not found");
            toast("OBD dongle not found. Please pair it first in Bluetooth settings.");
        }
    }

    /**
     * Show popup menu for navigation
     */
    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.getMenuInflater().inflate(R.menu.dashboard_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener((MenuItem item) -> {
            int id = item.getItemId();
            if (id == R.id.menu_account) {
                toast("Account Settings");
                return true;
            } else if (id == R.id.menu_live_tracking) {
                startActivity(new Intent(FetchIdsActivity.this, MapsActivity.class));
                return true;
            } else if (id == R.id.menu_connect_obd) {
                toast("Use 'Test Connection' button to connect to OBD");
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    /**
     * Check Python backend health
     */
    private void healthCheckPython() {
        api.sayHello().enqueue(new Callback<HelloResponse>() {
            @Override
            public void onResponse(Call<HelloResponse> call, Response<HelloResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tvPythonStatus.setText("Python: ✅ Connected");
                    tvPythonStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                } else {
                    tvPythonStatus.setText("Python: ❌ Error " + response.code());
                    tvPythonStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                }
            }

            @Override
            public void onFailure(Call<HelloResponse> call, Throwable t) {
                tvPythonStatus.setText("Python: ⚠️ " + t.getMessage());
                tvPythonStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            }
        });
    }

    /**
     * Check and request necessary permissions
     */
    private boolean checkAndRequestPermissions() {
        List<String> toRequest = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(permission);
            }
        }
        if (!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            toast(allGranted ? "Permissions granted." : "Some permissions denied. App may not work properly.");
        }
    }

    /**
     * Get and display the device's Android ID
     */
    private void displayPhoneAndroidId() {
        @SuppressLint("HardwareIds")
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        currentAndroidId = androidId;
        tvAndroidId.setText("Android ID: " + (androidId == null ? "-" : androidId));
        tvStatus.setText("Status: Android ID retrieved");
    }

    /**
     * Show dialog for manual entry of all IDs
     */
    private void showManualEntryDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);

        // User Name input
        final EditText etUserName = new EditText(this);
        etUserName.setHint("Your Name (e.g., John)");
        etUserName.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        root.addView(etUserName);

        // Car ID input
        final EditText etCarId = new EditText(this);
        etCarId.setHint("Car ID / VIN / Calibration ID / ECU Name");
        etCarId.setInputType(InputType.TYPE_CLASS_TEXT);
        etCarId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
        if (currentCarId != null) etCarId.setText(currentCarId);
        root.addView(etCarId);

        // Dongle MAC input
        final EditText etMac = new EditText(this);
        etMac.setHint("Dongle MAC (AA:BB:CC:DD:EE:FF)");
        etMac.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etMac.setFilters(new InputFilter[]{new InputFilter.LengthFilter(17)});
        if (currentMac != null) etMac.setText(currentMac);
        root.addView(etMac);

        // Android ID input
        final EditText etAndroidId = new EditText(this);
        etAndroidId.setHint("Android ID");
        etAndroidId.setInputType(InputType.TYPE_CLASS_TEXT);
        etAndroidId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        if (currentAndroidId != null) etAndroidId.setText(currentAndroidId);
        root.addView(etAndroidId);

        new AlertDialog.Builder(this)
                .setTitle("Enter IDs Manually")
                .setView(root)
                .setPositiveButton("Save", (dialog, which) -> {
                    String userName = safeTrim(etUserName.getText().toString());
                    String carId = safeTrim(etCarId.getText().toString());
                    String mac = safeTrim(etMac.getText().toString());
                    String androidId = safeTrim(etAndroidId.getText().toString());

                    if (isEmpty(userName)) {
                        toast("Your Name is required!");
                        return;
                    }
                    if (isEmpty(carId)) {
                        toast("Car ID is required!");
                        return;
                    }

                    SecureStorage.saveUserName(this, userName);
                    currentCarId = carId;
                    currentMac = mac;
                    currentAndroidId = androidId;
                    currentMode = "manual";

                    tvVin.setText("Car ID: " + currentCarId);
                    tvDongleMac.setText("Dongle MAC: " + (isEmpty(currentMac) ? "-" : currentMac));
                    tvAndroidId.setText("Android ID: " + (isEmpty(currentAndroidId) ? "-" : currentAndroidId));
                    tvStatus.setText("Status: Manual IDs saved");

                    toast("IDs and name saved successfully");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Show dialog for modifying existing IDs
     */
    private void showModifyIdsDialog() {
        String currentCarIdDisplay = extractValueOrFallback(tvVin.getText().toString(), currentCarId, "Car ID:");
        String currentMacDisplay = extractValueOrFallback(tvDongleMac.getText().toString(), currentMac, "Dongle MAC:");
        String currentAndroidIdDisplay = extractValueOrFallback(tvAndroidId.getText().toString(), currentAndroidId, "Android ID:");
        String currentUserName = SecureStorage.getUserName(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);

        final EditText etUserName = new EditText(this);
        etUserName.setHint("Your Name (e.g., John)");
        etUserName.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (!"Welcome".equals(currentUserName)) {
            etUserName.setText(currentUserName);
        }
        root.addView(etUserName);

        final EditText etCarId = new EditText(this);
        etCarId.setHint("Car ID / VIN / Calibration ID / ECU Name");
        etCarId.setInputType(InputType.TYPE_CLASS_TEXT);
        etCarId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
        if (!isEmpty(currentCarIdDisplay)) etCarId.setText(currentCarIdDisplay);
        root.addView(etCarId);

        final EditText etMac = new EditText(this);
        etMac.setHint("Dongle MAC (AA:BB:CC:DD:EE:FF)");
        etMac.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etMac.setFilters(new InputFilter[]{new InputFilter.LengthFilter(17)});
        if (!isEmpty(currentMacDisplay)) etMac.setText(currentMacDisplay);
        root.addView(etMac);

        final EditText etAndroidId = new EditText(this);
        etAndroidId.setHint("Android ID");
        etAndroidId.setInputType(InputType.TYPE_CLASS_TEXT);
        etAndroidId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        if (!isEmpty(currentAndroidIdDisplay)) etAndroidId.setText(currentAndroidIdDisplay);
        root.addView(etAndroidId);

        new AlertDialog.Builder(this)
                .setTitle("Modify IDs")
                .setView(root)
                .setPositiveButton("Update", (dialog, which) -> {
                    String userName = safeTrim(etUserName.getText().toString());
                    String carId = safeTrim(etCarId.getText().toString());
                    String mac = safeTrim(etMac.getText().toString());
                    String androidId = safeTrim(etAndroidId.getText().toString());

                    if (isEmpty(userName)) {
                        toast("Your Name is required!");
                        return;
                    }
                    if (isEmpty(carId)) {
                        toast("Car ID is required!");
                        return;
                    }

                    SecureStorage.saveUserName(this, userName);
                    currentCarId = carId;
                    currentMac = mac;
                    currentAndroidId = androidId;
                    currentMode = "manual";

                    tvVin.setText("Car ID: " + currentCarId);
                    tvDongleMac.setText("Dongle MAC: " + (isEmpty(currentMac) ? "-" : currentMac));
                    tvAndroidId.setText("Android ID: " + (isEmpty(currentAndroidId) ? "-" : currentAndroidId));
                    tvStatus.setText("Status: IDs modified");

                    toast("IDs and name updated successfully");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Register device with backend
     */
    private void registerWithBackend() {
        if (isEmpty(currentAndroidId)) {
            displayPhoneAndroidId();
        }

        String carId = extractValueOrFallback(tvVin.getText().toString(), currentCarId, "Car ID:");
        String mac = extractValueOrFallback(tvDongleMac.getText().toString(), currentMac, "Dongle MAC:");
        String androidId = currentAndroidId;
        String userName = SecureStorage.getUserName(this);

        if (isEmpty(carId)) {
            toast("Car ID is required for registration!");
            return;
        }
        if (isEmpty(mac)) {
            toast("Dongle MAC is required for registration!");
            return;
        }
        if (isEmpty(androidId)) {
            toast("Android ID is required for registration!");
            return;
        }
        if (userName == null || "Welcome".equals(userName) || isEmpty(userName)) {
            toast("User Name is required! Please set it using 'Enter Manually' or 'Modify IDs' first.");
            return;
        }

        tvStatus.setText("Status: Generating security keys...");

        if (!SecurityManager.keysExist()) {
            SecurityManager.generateKeyPair();
            toast("Security keys generated!");
        }

        String publicKey = SecurityManager.getPublicKeyString();
        if (publicKey == null) {
            toast("Failed to generate security keys!");
            tvStatus.setText("Status: Key generation failed");
            return;
        }

        tvStatus.setText("Status: Registering with backend...");

        RegisterRequest request = new RegisterRequest(carId, mac, androidId, currentMode, publicKey, userName);

        api.register(request).enqueue(new Callback<RegisterResponse>() {
            @Override
            public void onResponse(Call<RegisterResponse> call, Response<RegisterResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RegisterResponse body = response.body();
                    String role = body.role;

                    if (role == null || role.isEmpty()) {
                        role = "SECONDARY";
                    }

                    SecureStorage.saveUserRole(FetchIdsActivity.this, role.toUpperCase());
                    SecureStorage.setRegistered(FetchIdsActivity.this, true);
                    SecureStorage.saveVehicleDetails(FetchIdsActivity.this, androidId, carId, mac);

                    Log.i("FetchIdsActivity", "✅ User registered with role: " + role);

                    currentUserRole = role;
                    tvRole.setText("Assigned Role: " + role);

                    if (roleDetailsHeader.getVisibility() == View.GONE) {
                        roleDetailsHeader.setVisibility(View.VISIBLE);
                    }

                    roleDetailsBox.setVisibility(View.GONE);
                    tvToggleDetails.setText("▼ Show Details");

                    setDetail(tvDetailRole, "Role: " + role);
                    setDetail(tvDetailMode, "Mode: " + (isEmpty(body.mode) ? currentMode : body.mode));
                    setDetail(tvDetailCarId, "Car ID: " + carId);
                    setDetail(tvDetailMac, "Dongle MAC: " + mac);
                    setDetail(tvDetailAndroid, "Android ID: " + androidId);

                    String message = body.message != null ? body.message : "Registration successful!";
                    toast(message);
                    tvStatus.setText("Status: Registered successfully");
                } else {
                    String errorMsg = "Registration failed (" + response.code() + ")";
                    if (response.code() == 403) {
                        errorMsg = "Registration failed. Dongle MAC does not match vehicle owner.";
                    }
                    tvStatus.setText("Status: " + errorMsg);
                    toast(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<RegisterResponse> call, Throwable t) {
                tvStatus.setText("Status: Network error. Defaulting to MAIN user.");
                toast("Backend unreachable. Logged in as MAIN user.");

                String role = "MAIN";

                SecureStorage.saveUserRole(FetchIdsActivity.this, role.toUpperCase());
                SecureStorage.setRegistered(FetchIdsActivity.this, true);
                SecureStorage.saveVehicleDetails(FetchIdsActivity.this, androidId, carId, mac);

                currentUserRole = role;
                tvRole.setText("Assigned Role: " + role + " (Offline)");

                if (roleDetailsHeader.getVisibility() == View.GONE) {
                    roleDetailsHeader.setVisibility(View.VISIBLE);
                }
                roleDetailsBox.setVisibility(View.GONE);
                tvToggleDetails.setText("▼ Show Details");

                setDetail(tvDetailRole, "Role: " + role);
                setDetail(tvDetailMode, "Mode: " + currentMode);
                setDetail(tvDetailCarId, "Car ID: " + carId);
                setDetail(tvDetailMac, "Dongle MAC: " + mac);
                setDetail(tvDetailAndroid, "Android ID: " + androidId);
            }
        });
    }

    /**
     * Set text in a TextView safely
     */
    private void setDetail(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    /**
     * Extract value from label text or return fallback
     */
    private String extractValueOrFallback(String labelText, String fallback, String prefix) {
        if (labelText != null && labelText.startsWith(prefix)) {
            String value = labelText.substring(prefix.length()).trim();
            if (!isEmpty(value) && !"-".equals(value)) {
                return value;
            }
        }
        return fallback;
    }

    /**
     * Helper methods for string operations
     */
    private static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Show toast message
     */
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sharedObdManager != null && sharedObdManager.isConnected()) {
            sharedObdManager.unregisterListener(null);
        }
    }
}