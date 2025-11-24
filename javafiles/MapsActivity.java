package com.example.findingbunks_part2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.findingbunks_part2.security.RoleChecker;
import com.example.findingbunks_part2.security.SecureStorage;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.*;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchByTextRequest;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.maps.android.PolyUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback,
        PetrolStationAdapter.OnStationClickListener, SharedObdManager.ObdDataListener {

    private static final String TAG = "PetrolFinderApp";
    private static final int PERMISSIONS_REQUEST_CODE = 1;

    // Map and Circles
    private Circle searchRadiusCircle;
    private Circle safeRangeCircle;
    private GoogleMap mMap;
    private int currentMapType = GoogleMap.MAP_TYPE_NORMAL;

    // Location
    private FusedLocationProviderClient fusedLocationProviderClient;
    private PlacesClient placesClient;
    private SharedObdManager sharedObdManager;
    private RequestQueue requestQueue;
    private String apiKey;
    private Location lastKnownLocation;
    private boolean locationPermissionGranted;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private boolean isLocationTrackingActive = false;
    private boolean isInNavigationMode = false;
    private Polyline currentRoute;
    private float obdEstimatedRangeKm = -1;

    // UI Components - Main
    private RecyclerView recyclerViewStations;
    private PetrolStationAdapter stationAdapter;
    private FloatingActionButton fabConnectObd;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    private TextView tvFuelLeft, tvEstimatedRange;
    private SwitchCompat switchMode;
    private EditText etManualRange;
    private LinearLayout searchResultsLayout, navigationLayout, vehicleParametersLayout;
    private TextView tvNavigatingTo;
    private Button btnStopNavigation;
    private ImageView ivLiveTrackingToggle;
    private Button trackingButtonLayout;
    private TextView tvTrackingStatus;

    // Enhanced UI Components
    private TextView tvQuickRange, tvQuickStations;
    private ImageView ivMapTypeNormal, ivMapTypeSatellite, ivMapTypeTerrain;
    private ImageView ivSortStations;
    private ChipGroup chipGroupFilter;
    private Chip chipShowAll, chipShowSafe, chipShowRisky;

    // Vehicle Data Sheet
    private BottomSheetBehavior<LinearLayout> vehicleDataSheetBehavior;
    private FloatingActionButton fabVehicleData;
    private TextView tvAppTitle;
    private TextView tvOfflineNotification;
    private Gson gson;
    private TextView tvNavStatus;
    private RecyclerView rvNavigationSteps;
    private TextView tvVehicleSpeed, tvVehicleRpm, tvVehicleEngineLoad, tvVehicleThrottle, tvVehicleDtc;

    // Voice Notifications
    private VoiceNotificationManager voiceNotificationManager;
    private SwitchCompat switchVoiceNotifications;

    // Filter state
    private String currentFilter = "all";
    private List<PetrolStation> allStationsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        Log.i(TAG, "--- App Starting ---");

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_maps_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            View topBar = findViewById(R.id.topBar);
            topBar.setPadding(topBar.getPaddingLeft(), systemBars.top,
                    topBar.getPaddingRight(), topBar.getPaddingBottom());

            View bottomSheet = findViewById(R.id.bottom_sheet);
            bottomSheet.setPadding(0, 0, 0, systemBars.bottom);

            View vehicleDataSheet = findViewById(R.id.vehicle_data_bottom_sheet);
            vehicleDataSheet.setPadding(0, 0, 0, systemBars.bottom);

            return insets;
        });

        initializeUiViews();
        initializeMapAndLocation();
        sharedObdManager = SharedObdManager.getInstance();
        loadLastKnownData();

        tvVehicleSpeed = findViewById(R.id.tvVehicleSpeed);
        tvVehicleRpm = findViewById(R.id.tvVehicleRpm);
        tvVehicleEngineLoad = findViewById(R.id.tvVehicleEngineLoad);
        tvVehicleThrottle = findViewById(R.id.tvVehicleThrottle);
        tvVehicleDtc = findViewById(R.id.tvVehicleDtc);

        // Initialize voice notifications
        voiceNotificationManager = new VoiceNotificationManager(this);
        boolean voiceEnabled = SecureStorage.getVoiceNotificationsEnabled(this, true);
        voiceNotificationManager.setEnabled(voiceEnabled);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sharedObdManager.registerListener(this);
        if (isLocationTrackingActive) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sharedObdManager.unregisterListener(this);
        stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sharedObdManager.unregisterListener(this);

        // Shutdown voice notifications
        if (voiceNotificationManager != null) {
            voiceNotificationManager.shutdown();
        }
    }

    private void loadLastKnownData() {
        if (sharedObdManager.isConnected()) return;

        float[] data = SecureStorage.getLastKnownVehicleData(this);
        float range = data[0];
        float fuel = data[1];

        long lastSyncTime = SecureStorage.getLastSyncTime(this);
        String timeAgo = "";
        if (lastSyncTime > 0) {
            long diff = (System.currentTimeMillis() - lastSyncTime) / 60000;
            if (diff < 60) {
                timeAgo = " (" + diff + "m ago)";
            } else if (diff < 1440) {
                timeAgo = " (" + (diff / 60) + "h ago)";
            }
        }

        if (range >= 0) {
            tvEstimatedRange.setText(String.format(Locale.getDefault(), "~%.0f km%s", range, timeAgo));
            tvQuickRange.setText(String.format(Locale.getDefault(), "Range: ~%.0f km", range));
        }
        if (fuel >= 0) {
            tvFuelLeft.setText(String.format(Locale.getDefault(), "~%.1f L", fuel));
        }
    }

    private void initializeUiViews() {
        tvOfflineNotification = findViewById(R.id.tvOfflineNotification);
        gson = new Gson();
        tvAppTitle = findViewById(R.id.tvAppTitle);

        LinearLayout bottomSheet = findViewById(R.id.bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        fabConnectObd = findViewById(R.id.fabConnectObd);
        recyclerViewStations = findViewById(R.id.recyclerViewStations);
        tvFuelLeft = findViewById(R.id.tvFuelLeft);
        tvEstimatedRange = findViewById(R.id.tvEstimatedRange);
        switchMode = findViewById(R.id.switchMode);
        etManualRange = findViewById(R.id.etManualRange);
        searchResultsLayout = findViewById(R.id.search_results_layout);
        navigationLayout = findViewById(R.id.navigation_layout);
        tvNavigatingTo = findViewById(R.id.tvNavigatingTo);
        btnStopNavigation = findViewById(R.id.btnStopNavigation);
        vehicleParametersLayout = findViewById(R.id.vehicle_parameters_layout);
        ivLiveTrackingToggle = findViewById(R.id.ivLiveTrackingToggle);
        trackingButtonLayout = findViewById(R.id.trackingButtonLayout);
        tvTrackingStatus = findViewById(R.id.tvTrackingStatus);

        tvQuickRange = findViewById(R.id.tvQuickRange);
        tvQuickStations = findViewById(R.id.tvQuickStations);
        ivMapTypeNormal = findViewById(R.id.ivMapTypeNormal);
        ivMapTypeSatellite = findViewById(R.id.ivMapTypeSatellite);
        ivMapTypeTerrain = findViewById(R.id.ivMapTypeTerrain);
        ivSortStations = findViewById(R.id.ivSortStations);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        chipShowAll = findViewById(R.id.chipShowAll);
        chipShowSafe = findViewById(R.id.chipShowSafe);
        chipShowRisky = findViewById(R.id.chipShowRisky);

        fabVehicleData = findViewById(R.id.fabVehicleData);
        LinearLayout vehicleDataSheet = findViewById(R.id.vehicle_data_bottom_sheet);
        vehicleDataSheetBehavior = BottomSheetBehavior.from(vehicleDataSheet);

        tvNavStatus = findViewById(R.id.tvNavStatus);
        rvNavigationSteps = findViewById(R.id.rvNavigationSteps);
        rvNavigationSteps.setLayoutManager(new LinearLayoutManager(this));

        String userName = SecureStorage.getUserName(this);
        if (userName != null && !userName.equals("Welcome")) {
            tvAppTitle.setText("Welcome, " + userName + "!");
        } else {
            tvAppTitle.setText(R.string.app_name_full);
        }

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        recyclerViewStations.setLayoutManager(new LinearLayoutManager(this));
        stationAdapter = new PetrolStationAdapter();
        stationAdapter.setOnStationClickListener(this);
        recyclerViewStations.setAdapter(stationAdapter);

        vehicleDataSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // Voice notifications toggle
        switchVoiceNotifications = findViewById(R.id.switchVoiceNotifications);
        boolean voiceEnabled = SecureStorage.getVoiceNotificationsEnabled(this, true);
        switchVoiceNotifications.setChecked(voiceEnabled);

        switchVoiceNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (voiceNotificationManager != null) {
                voiceNotificationManager.setEnabled(isChecked);
                SecureStorage.saveVoiceNotificationsEnabled(this, isChecked);
                Toast.makeText(this,
                        isChecked ? "🔊 Voice notifications enabled" : "🔇 Voice notifications disabled",
                        Toast.LENGTH_SHORT).show();
            }
        });

        setupClickListeners();
        applyRoleBasedUI();
    }

    private void setupClickListeners() {
        fabVehicleData.setOnClickListener(v -> {
            if (vehicleDataSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                if (bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
                vehicleDataSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            } else {
                vehicleDataSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        fabConnectObd.setOnClickListener(v -> {
            if (!sharedObdManager.isConnected()) {
                sharedObdManager.connect(this);
            } else {
                sharedObdManager.disconnect();
            }
        });

        trackingButtonLayout.setOnClickListener(v -> toggleLiveTracking());

        switchMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                etManualRange.setVisibility(View.VISIBLE);
                fabConnectObd.setEnabled(false);
                if (sharedObdManager.isConnected()) sharedObdManager.disconnect();
                updateRangeFromManualInput();
            } else {
                etManualRange.setVisibility(View.GONE);
                fabConnectObd.setEnabled(true);
                updateRangeFromObd();
            }
            if (isLocationTrackingActive) findAndShowNearestStations();
        });

        etManualRange.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateRangeFromManualInput();
                if (isLocationTrackingActive) findAndShowNearestStations();
            }
        });

        btnStopNavigation.setOnClickListener(v -> exitNavigationMode());

        ivMapTypeNormal.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_NORMAL, ivMapTypeNormal));
        ivMapTypeSatellite.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_SATELLITE, ivMapTypeSatellite));
        ivMapTypeTerrain.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_TERRAIN, ivMapTypeTerrain));

        ivSortStations.setOnClickListener(v -> showSortDialog());

        chipShowAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentFilter = "all";
                applyFilter();
            }
        });

        chipShowSafe.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentFilter = "safe";
                applyFilter();
            }
        });

        chipShowRisky.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentFilter = "risky";
                applyFilter();
            }
        });
    }

    private void changeMapType(int mapType, ImageView selectedIcon) {
        if (mMap != null) {
            mMap.setMapType(mapType);
            currentMapType = mapType;

            ivMapTypeNormal.setColorFilter(getResources().getColor(android.R.color.darker_gray));
            ivMapTypeSatellite.setColorFilter(getResources().getColor(android.R.color.darker_gray));
            ivMapTypeTerrain.setColorFilter(getResources().getColor(android.R.color.darker_gray));

            selectedIcon.setColorFilter(getResources().getColor(android.R.color.holo_blue_dark));

            Animation pulse = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
            selectedIcon.startAnimation(pulse);

            String typeName = mapType == GoogleMap.MAP_TYPE_NORMAL ? "Normal" :
                    mapType == GoogleMap.MAP_TYPE_SATELLITE ? "Satellite" : "Terrain";
            Toast.makeText(this, "Map Type: " + typeName, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSortDialog() {
        String[] options = {"Distance (Nearest First)", "Distance (Farthest First)", "Name (A-Z)", "Name (Z-A)"};

        new AlertDialog.Builder(this)
                .setTitle("Sort Stations By")
                .setItems(options, (dialog, which) -> {
                    List<PetrolStation> currentList = new ArrayList<>(allStationsList);

                    switch (which) {
                        case 0:
                            Collections.sort(currentList);
                            break;
                        case 1:
                            Collections.sort(currentList, (a, b) -> Float.compare(b.getDistance(), a.getDistance()));
                            break;
                        case 2:
                            Collections.sort(currentList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                            break;
                        case 3:
                            Collections.sort(currentList, (a, b) -> b.getName().compareToIgnoreCase(a.getName()));
                            break;
                    }

                    allStationsList = currentList;
                    applyFilter();
                })
                .show();
    }

    private void applyFilter() {
        if (allStationsList.isEmpty()) return;

        List<PetrolStation> filteredList = new ArrayList<>();

        for (PetrolStation station : allStationsList) {
            switch (currentFilter) {
                case "all":
                    filteredList.add(station);
                    break;
                case "safe":
                    if (station.isReachable()) {
                        filteredList.add(station);
                    }
                    break;
                case "risky":
                    if (!station.isReachable()) {
                        filteredList.add(station);
                    }
                    break;
            }
        }

        stationAdapter.setStations(filteredList);
        tvQuickStations.setText("Stations: " + filteredList.size());
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    public void onObdDataReceived(String speed, float fuelLeft, float estimatedRange) {
        runOnUiThread(() -> {
            tvFuelLeft.setText(String.format(Locale.getDefault(), "%.1f L", fuelLeft));
            tvVehicleSpeed.setText(speed);

            SecureStorage.saveLastKnownVehicleData(this, estimatedRange, fuelLeft);
            obdEstimatedRangeKm = estimatedRange;

            if (!switchMode.isChecked()) {
                updateRangeFromObd();
                tvQuickRange.setText(String.format(Locale.getDefault(), "Range: %.0f km", estimatedRange));
            }

            // Voice: Announce fuel range warnings
            if (voiceNotificationManager != null && !allStationsList.isEmpty()) {
                PetrolStation nearest = allStationsList.get(0);
                voiceNotificationManager.announceFuelRangeWarning(
                        estimatedRange,
                        nearest.getDistance()
                );
            }
        });
    }

    @Override
    public void onObdStatusChanged(String status) {
        runOnUiThread(() -> {
            Toast.makeText(this, "OBD Status: " + status, Toast.LENGTH_SHORT).show();
            if (status.equals("Connected")) {
                fabConnectObd.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_bluetooth_connected));
            } else {
                fabConnectObd.setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.stat_sys_data_bluetooth));
                loadLastKnownData();
            }
        });
    }

    @Override
    public void onVinReceived(String vin) {
        // Not used in MapsActivity
    }

    @Override
    public void onStationClick(PetrolStation station) {
        enterNavigationMode(station);
    }

    private void enterNavigationMode(PetrolStation station) {
        if (lastKnownLocation == null) {
            Toast.makeText(this, "Current location unknown. Cannot navigate.", Toast.LENGTH_SHORT).show();
            return;
        }

        isInNavigationMode = true;
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        vehicleParametersLayout.setVisibility(View.GONE);
        searchResultsLayout.setVisibility(View.GONE);
        navigationLayout.setVisibility(View.VISIBLE);
        tvNavigatingTo.setText("Navigating to: " + station.getName());
        fabConnectObd.hide();
        fabVehicleData.hide();
        trackingButtonLayout.setVisibility(View.GONE);

        // Voice: Announce navigation started
        if (voiceNotificationManager != null) {
            voiceNotificationManager.announceNavigationStarted(
                    station.getName(),
                    station.getDistance()
            );
        }

        String stationId = station.getLocation().latitude + "," + station.getLocation().longitude;

        if (isNetworkAvailable()) {
            tvNavStatus.setVisibility(View.GONE);
            drawRouteAndCacheSteps(
                    new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()),
                    station.getLocation(),
                    stationId
            );
        } else {
            tvOfflineNotification.setVisibility(View.VISIBLE);
            tvNavStatus.setText("📡 Offline Mode: Showing cached steps");
            tvNavStatus.setVisibility(View.VISIBLE);

            String stepsJson = SecureStorage.getNavigationSteps(this, stationId);
            if (stepsJson != null) {
                Type type = new TypeToken<ArrayList<String>>() {}.getType();
                List<String> steps = gson.fromJson(stepsJson, type);
                rvNavigationSteps.setAdapter(new NavigationStepAdapter(steps));
            } else {
                tvNavStatus.setText("⚠️ Offline: No cached route for this station");
            }
        }
    }

    private void drawRouteAndCacheSteps(LatLng origin, LatLng destination, String stationId) {
        String url = "https://maps.googleapis.com/maps/api/directions/json?origin=" +
                origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                "&key=" + apiKey;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray routes = response.getJSONArray("routes");
                        if (routes.length() > 0) {
                            JSONObject route = routes.getJSONObject(0);

                            String encodedPolyline = route.getJSONObject("overview_polyline").getString("points");
                            List<LatLng> decodedPath = PolyUtil.decode(encodedPolyline);
                            if (currentRoute != null) currentRoute.remove();
                            currentRoute = mMap.addPolyline(new PolylineOptions()
                                    .addAll(decodedPath)
                                    .width(15)
                                    .color(Color.BLUE)
                                    .geodesic(true));

                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            for (LatLng point : decodedPath) {
                                builder.include(point);
                            }
                            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150));

                            List<String> stepInstructions = new ArrayList<>();
                            JSONArray legs = route.getJSONArray("legs");
                            if (legs.length() > 0) {
                                JSONArray steps = legs.getJSONObject(0).getJSONArray("steps");
                                for (int i = 0; i < steps.length(); i++) {
                                    String htmlInstruction = steps.getJSONObject(i).getString("html_instructions");
                                    stepInstructions.add(htmlInstruction);
                                }
                            }

                            String stepsJson = gson.toJson(stepInstructions);
                            SecureStorage.saveNavigationSteps(this, stationId, stepsJson);

                            rvNavigationSteps.setAdapter(new NavigationStepAdapter(stepInstructions));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing directions", e);
                        tvNavStatus.setText("⚠️ Error parsing route");
                        tvNavStatus.setVisibility(View.VISIBLE);
                    }
                }, error -> {
            Log.e(TAG, "Directions API failed", error);
            tvNavStatus.setText("❌ Failed to fetch route");
            tvNavStatus.setVisibility(View.VISIBLE);
        });
        requestQueue.add(request);
    }

    private void exitNavigationMode() {
        isInNavigationMode = false;
        if (currentRoute != null) currentRoute.remove();
        if (searchRadiusCircle != null) searchRadiusCircle.remove();
        if (safeRangeCircle != null) safeRangeCircle.remove();

        vehicleParametersLayout.setVisibility(View.VISIBLE);
        searchResultsLayout.setVisibility(View.VISIBLE);
        navigationLayout.setVisibility(View.GONE);
        fabConnectObd.show();

        rvNavigationSteps.setAdapter(null);
        tvNavStatus.setVisibility(View.GONE);

        if (RoleChecker.isMainUser(this)) {
            fabVehicleData.show();
        }

        trackingButtonLayout.setVisibility(View.VISIBLE);

        // Voice: Reset voice state when exiting navigation
        if (voiceNotificationManager != null) {
            voiceNotificationManager.reset();
        }

        if (lastKnownLocation != null) {
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                    new CameraPosition.Builder()
                            .target(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()))
                            .zoom(12f)
                            .bearing(0)
                            .tilt(0)
                            .build()));
        }
        findAndShowNearestStations();
    }

    private void findAndShowNearestStations() {
        if (mMap == null || !locationPermissionGranted || lastKnownLocation == null) return;
        if (isInNavigationMode) return;

        mMap.clear();

        if (vehicleDataSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
            vehicleDataSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }

        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }

        float currentRangeToUse = -1;
        if (switchMode.isChecked()) {
            try {
                currentRangeToUse = Float.parseFloat(etManualRange.getText().toString());
            } catch (NumberFormatException e) {
                currentRangeToUse = -1;
            }
        } else {
            currentRangeToUse = obdEstimatedRangeKm;
        }

        final float rangeForCheck = currentRangeToUse;
        LatLng currentLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());

        double searchRadiusMeters;
        if (rangeForCheck > 0) {
            searchRadiusMeters = Math.min(rangeForCheck * 1000 * 1.2, 50000);
        } else {
            searchRadiusMeters = 10000;
        }
        drawSearchRadiusCircles(currentLatLng, searchRadiusMeters, rangeForCheck);

        if (isNetworkAvailable()) {
            Log.d(TAG, "Online. Fetching fresh data...");
            tvOfflineNotification.setVisibility(View.GONE);

            List<Place.Field> placeFields = Arrays.asList(Place.Field.NAME, Place.Field.LAT_LNG,
                    Place.Field.ADDRESS, Place.Field.ID);
            CircularBounds bounds = CircularBounds.newInstance(currentLatLng, searchRadiusMeters);
            SearchByTextRequest request = SearchByTextRequest.builder("gas station", placeFields)
                    .setLocationBias(bounds)
                    .setMaxResultCount(20)
                    .build();

            placesClient.searchByText(request).addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    List<Place> places = task.getResult().getPlaces();
                    List<PetrolStation> stationList = new ArrayList<>();

                    for (Place place : places) {
                        if (place.getLatLng() == null) continue;
                        float[] results = new float[1];
                        Location.distanceBetween(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(),
                                place.getLatLng().latitude, place.getLatLng().longitude, results);
                        float distanceInKm = results[0] / 1000;

                        boolean isReachable = (rangeForCheck > 0) && (distanceInKm <= (rangeForCheck * 0.8f));

                        stationList.add(new PetrolStation(place.getName(), place.getAddress(),
                                distanceInKm, place.getLatLng(), isReachable));

                        mMap.addMarker(new MarkerOptions()
                                .title(place.getName())
                                .snippet(String.format("%.1f km away - %s", distanceInKm, isReachable ? "Safe" : "Risky"))
                                .position(place.getLatLng())
                                .icon(bitmapDescriptorFromVector(this, R.drawable.ic_gas_station))
                        );
                    }

                    Collections.sort(stationList);
                    allStationsList = new ArrayList<>(stationList);
                    stationAdapter.setStations(stationList);
                    tvQuickStations.setText("Stations: " + stationList.size());

                    String bunksJson = gson.toJson(stationList);
                    SecureStorage.saveBunksJson(MapsActivity.this, bunksJson);
                    Log.d(TAG, "Saved " + stationList.size() + " bunks to local cache.");

                    // Voice: Announce station updates
                    if (voiceNotificationManager != null) {
                        voiceNotificationManager.announceStationUpdates(
                                stationList,
                                isInNavigationMode
                        );
                    }

                    preCacheNavigationSteps(stationList);

                } else {
                    Log.e(TAG, "Places search failed: " + task.getException());
                }
            });

        } else {
            Log.d(TAG, "Offline. Loading from cache...");
            tvOfflineNotification.setVisibility(View.VISIBLE);

            String cachedJson = SecureStorage.getBunksJson(this);
            if (cachedJson != null && !cachedJson.isEmpty()) {
                Type type = new TypeToken<ArrayList<PetrolStation>>() {}.getType();
                List<PetrolStation> cachedList = gson.fromJson(cachedJson, type);

                if (cachedList != null) {
                    Log.d(TAG, "Loaded " + cachedList.size() + " bunks from cache.");

                    List<PetrolStation> updatedList = new ArrayList<>();
                    for (PetrolStation station : cachedList) {
                        float[] results = new float[1];
                        Location.distanceBetween(
                                lastKnownLocation.getLatitude(),
                                lastKnownLocation.getLongitude(),
                                station.getLocation().latitude,
                                station.getLocation().longitude,
                                results
                        );
                        float distanceInKm = results[0] / 1000;

                        boolean isReachable = (rangeForCheck > 0) && (distanceInKm <= (rangeForCheck * 0.8f));

                        PetrolStation updatedStation = new PetrolStation(
                                station.getName(),
                                station.getAddress(),
                                distanceInKm,
                                station.getLocation(),
                                isReachable
                        );
                        updatedList.add(updatedStation);

                        mMap.addMarker(new MarkerOptions()
                                .title(station.getName())
                                .snippet(String.format("%.1f km away - %s", distanceInKm, isReachable ? "Safe" : "Risky"))
                                .position(station.getLocation())
                                .icon(bitmapDescriptorFromVector(this, R.drawable.ic_gas_station))
                        );
                    }

                    Collections.sort(updatedList);
                    allStationsList = new ArrayList<>(updatedList);
                    stationAdapter.setStations(updatedList);
                    tvQuickStations.setText("Stations: " + updatedList.size());

                    // Voice: Announce station updates (offline mode)
                    if (voiceNotificationManager != null) {
                        voiceNotificationManager.announceStationUpdates(
                                updatedList,
                                isInNavigationMode
                        );
                    }
                }
            } else {
                Log.d(TAG, "No cached data found.");
                Toast.makeText(this, "Offline and no cached data available.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void preCacheNavigationSteps(List<PetrolStation> stationList) {
        if (lastKnownLocation == null) return;

        Log.i(TAG, "Pre-caching navigation steps for top 3 bunks...");

        LatLng origin = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());

        final int BUNKS_TO_CACHE = 3;

        for (int i = 0; i < Math.min(stationList.size(), BUNKS_TO_CACHE); i++) {
            PetrolStation station = stationList.get(i);
            String stationId = station.getLocation().latitude + "," + station.getLocation().longitude;

            if (SecureStorage.getNavigationSteps(this, stationId) == null) {
                Log.d(TAG, "Caching steps for: " + station.getName());
                fetchAndCacheSteps(origin, station.getLocation(), stationId);
            }
        }
    }

    private void fetchAndCacheSteps(LatLng origin, LatLng destination, String stationId) {
        String url = "https://maps.googleapis.com/maps/api/directions/json?origin=" +
                origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                "&key=" + apiKey;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray routes = response.getJSONArray("routes");
                        if (routes.length() > 0) {
                            JSONObject route = routes.getJSONObject(0);

                            List<String> stepInstructions = new ArrayList<>();
                            JSONArray legs = route.getJSONArray("legs");
                            if (legs.length() > 0) {
                                JSONArray steps = legs.getJSONObject(0).getJSONArray("steps");
                                for (int i = 0; i < steps.length(); i++) {
                                    String htmlInstruction = steps.getJSONObject(i).getString("html_instructions");
                                    stepInstructions.add(htmlInstruction);
                                }
                            }

                            String stepsJson = gson.toJson(stepInstructions);
                            SecureStorage.saveNavigationSteps(this, stationId, stepsJson);
                            Log.i(TAG, "✅ Pre-cached steps for station: " + stationId);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error pre-caching directions", e);
                    }
                }, error -> {
            Log.e(TAG, "Directions API failed (pre-cache)", error);
        });
        requestQueue.add(request);
    }

    private void applyRoleBasedUI() {
        boolean isMainUser = RoleChecker.isMainUser(this);
        String role = RoleChecker.getRole(this);
        Log.i(TAG, "Applying UI for role: " + role + " (Is Main: " + isMainUser + ")");

        if (isMainUser) {
            fabVehicleData.setVisibility(View.VISIBLE);
        } else {
            fabVehicleData.setVisibility(View.GONE);
            if (vehicleDataSheetBehavior != null) {
                vehicleDataSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        }
    }

    private void initializeMapAndLocation() {
        try {
            apiKey = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_META_DATA).metaData.getString("com.google.android.geo.API_KEY");
            if (!Places.isInitialized()) Places.initialize(getApplicationContext(), apiKey);
        } catch (Exception e) {
            Log.e(TAG, "Failed to init Places", e);
            return;
        }
        placesClient = Places.createClient(this);
        requestQueue = Volley.newRequestQueue(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
        createLocationRequest();
        createLocationCallback();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        try {
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
        } catch (Exception e) {
            Log.e(TAG, "Style parsing failed.", e);
        }
        getLocationPermission();
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location newLocation = locationResult.getLastLocation();
                if (newLocation == null) return;

                if (isInNavigationMode && isNetworkAvailable()) {
                    LatLng newLatLng = new LatLng(newLocation.getLatitude(), newLocation.getLongitude());
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(newLatLng));
                } else if (!isInNavigationMode && isLocationTrackingActive) {
                    findAndShowNearestStations();
                }
                lastKnownLocation = newLocation;
            }
        };
    }

    private void drawSearchRadiusCircles(LatLng center, double searchRadiusMeters, float userRangeKm) {
        if (searchRadiusCircle != null) searchRadiusCircle.remove();
        if (safeRangeCircle != null) safeRangeCircle.remove();

        searchRadiusCircle = mMap.addCircle(new CircleOptions()
                .center(center)
                .radius(searchRadiusMeters)
                .strokeColor(Color.argb(150, 33, 150, 243))
                .strokeWidth(3)
                .fillColor(Color.argb(30, 33, 150, 243)));

        if (userRangeKm > 0) {
            double safeRangeMeters = userRangeKm * 1000 * 0.8;
            safeRangeCircle = mMap.addCircle(new CircleOptions()
                    .center(center)
                    .radius(safeRangeMeters)
                    .strokeColor(Color.argb(200, 76, 175, 80))
                    .strokeWidth(4)
                    .fillColor(Color.argb(50, 76, 175, 80)));
        }
    }

    private void updateRangeFromManualInput() {
        try {
            float manualRange = Float.parseFloat(etManualRange.getText().toString());
            tvEstimatedRange.setText(String.format(Locale.getDefault(), "%.0f km", manualRange));
            tvQuickRange.setText(String.format(Locale.getDefault(), "Range: %.0f km", manualRange));
        } catch (NumberFormatException e) {
            tvEstimatedRange.setText("-- km");
            tvQuickRange.setText("Range: -- km");
        }
    }

    private void updateRangeFromObd() {
        if (obdEstimatedRangeKm >= 0) {
            tvEstimatedRange.setText(String.format(Locale.getDefault(), "%.0f km", obdEstimatedRangeKm));
            tvQuickRange.setText(String.format(Locale.getDefault(), "Range: %.0f km", obdEstimatedRangeKm));
        } else {
            tvEstimatedRange.setText("-- km");
            tvQuickRange.setText("Range: -- km");
        }
    }

    private void getLocationPermission() {
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_NETWORK_STATE};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_NETWORK_STATE};
        }

        boolean permissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
                break;
            }
        }

        if (permissionsGranted) {
            locationPermissionGranted = true;
            getDeviceLocation();
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int r, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if (r == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : g) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                locationPermissionGranted = true;
                getDeviceLocation();
            } else {
                Toast.makeText(this, "Permissions are required for the app to function.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void getDeviceLocation() {
        if (locationPermissionGranted) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
            fusedLocationProviderClient.getLastLocation().addOnCompleteListener(this, task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    lastKnownLocation = task.getResult();
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(lastKnownLocation.getLatitude(),
                                    lastKnownLocation.getLongitude()), 12));

                    findAndShowNearestStations();
                }
            });
        }
    }

    private BitmapDescriptor bitmapDescriptorFromVector(Context c, int v) {
        Drawable d = ContextCompat.getDrawable(c, v);
        if (d == null) return null;
        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        Bitmap b = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas can = new Canvas(b);
        d.draw(can);
        return BitmapDescriptorFactory.fromBitmap(b);
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(5000)
                .build();
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (!locationPermissionGranted) return;

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback,
                Looper.getMainLooper());
        isLocationTrackingActive = true;

        ivLiveTrackingToggle.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        tvTrackingStatus.setText("Stop Tracking");
        tvTrackingStatus.setVisibility(View.VISIBLE);

        Animation pulse = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        trackingButtonLayout.startAnimation(pulse);

        Toast.makeText(this, "🌐 Live tracking started!", Toast.LENGTH_SHORT).show();
        findAndShowNearestStations();
    }

    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        isLocationTrackingActive = false;

        ivLiveTrackingToggle.setImageResource(android.R.drawable.ic_menu_search);
        tvTrackingStatus.setText("Start Tracking");
        tvTrackingStatus.setVisibility(View.GONE);

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        vehicleDataSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        if (mMap != null) mMap.clear();
        if (searchRadiusCircle != null) searchRadiusCircle.remove();
        if (safeRangeCircle != null) safeRangeCircle.remove();

        // Voice: Announce tracking stopped
        if (voiceNotificationManager != null) {
            voiceNotificationManager.announceTrackingStopped();
        }

        Toast.makeText(this, "⏸️ Tracking stopped", Toast.LENGTH_SHORT).show();
    }

    private void toggleLiveTracking() {
        if (!isLocationTrackingActive) {
            startLocationUpdates();
        } else {
            stopLocationUpdates();
            if (isInNavigationMode) exitNavigationMode();
        }
    }
}