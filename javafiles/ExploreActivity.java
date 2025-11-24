package com.example.findingbunks_part2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchByTextRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ExploreActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "ExploreActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private static final double SEARCH_RADIUS_METERS = 15000;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private PlacesClient placesClient;
    private String apiKey;

    private Location lastKnownLocation;
    private boolean locationPermissionGranted;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    private BitmapDescriptor bunkIcon, repairIcon, foodIcon, atmIcon, hospitalIcon, parkingIcon;

    // UI Elements
    private CardView infoCard;
    private TextView tvPlaceName, tvPlaceAddress, tvPlaceDistance;
    private ImageView ivPlaceIcon;
    private LinearLayout btnGetDirections;
    private View loadingOverlay;
    private TextView tvLoadingText;

    // State tracking
    private String currentSearchType = "";
    private Marker selectedMarker;
    private int placesFoundCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, 0);
            findViewById(R.id.topBar).setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            findViewById(R.id.bottomBar).setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeUiViews();
        initializeMapAndLocation();
    }

    private void initializeUiViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        LinearLayout btnFindBunks = findViewById(R.id.btnFindBunks);
        LinearLayout btnFindRepairs = findViewById(R.id.btnFindRepairs);
        LinearLayout btnFindFood = findViewById(R.id.btnFindFood);
        LinearLayout btnFindAtm = findViewById(R.id.btnFindAtm);
        LinearLayout btnFindHospital = findViewById(R.id.btnFindHospital);
        LinearLayout btnFindParking = findViewById(R.id.btnFindParking);

        infoCard = findViewById(R.id.infoCard);
        tvPlaceName = findViewById(R.id.tvPlaceName);
        tvPlaceAddress = findViewById(R.id.tvPlaceAddress);
        tvPlaceDistance = findViewById(R.id.tvPlaceDistance);
        ivPlaceIcon = findViewById(R.id.ivPlaceIcon);
        btnGetDirections = findViewById(R.id.btnGetDirections);
        ImageView ivCloseInfo = findViewById(R.id.ivCloseInfo);

        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        ivBack.setOnClickListener(v -> finish());
        ivCloseInfo.setOnClickListener(v -> hideInfoCard());

        // Create custom marker icons with better colors
        bunkIcon = bitmapDescriptorFromVector(this, R.drawable.ic_gas_station, R.color.md_deep_orange_500);
        repairIcon = bitmapDescriptorFromVector(this, android.R.drawable.ic_menu_manage, R.color.md_blue_600);
        foodIcon = bitmapDescriptorFromVector(this, android.R.drawable.ic_menu_compass, R.color.md_green_600);
        atmIcon = bitmapDescriptorFromVector(this, android.R.drawable.ic_secure, R.color.md_teal_600);
        hospitalIcon = bitmapDescriptorFromVector(this, android.R.drawable.ic_menu_add, R.color.md_red_600);
        parkingIcon = bitmapDescriptorFromVector(this, android.R.drawable.ic_menu_mylocation, R.color.md_purple_600);

        btnFindBunks.setOnClickListener(v -> searchWithAnimation("gas station", bunkIcon, "Bunks", btnFindBunks));
        btnFindRepairs.setOnClickListener(v -> searchWithAnimation("car repair", repairIcon, "Repairs", btnFindRepairs));
        btnFindFood.setOnClickListener(v -> searchWithAnimation("restaurant", foodIcon, "Food", btnFindFood));
        btnFindAtm.setOnClickListener(v -> searchWithAnimation("atm", atmIcon, "ATMs", btnFindAtm));
        btnFindHospital.setOnClickListener(v -> searchWithAnimation("hospital", hospitalIcon, "Hospitals", btnFindHospital));
        btnFindParking.setOnClickListener(v -> searchWithAnimation("parking", parkingIcon, "Parking", btnFindParking));
    }

    private void searchWithAnimation(String query, BitmapDescriptor icon, String displayName, View button) {
        // Animate button press
        button.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction(() ->
                button.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        ).start();

        currentSearchType = displayName;
        showLoading("Finding nearby " + displayName.toLowerCase() + "...");
        hideInfoCard();
        findNearbyPlaces(query, icon, displayName);
    }

    private void initializeMapAndLocation() {
        try {
            apiKey = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA)
                    .metaData.getString("com.google.android.geo.API_KEY");
            if (!Places.isInitialized()) Places.initialize(getApplicationContext(), apiKey);
        } catch (Exception e) {
            Log.e(TAG, "Failed to init Places", e);
            return;
        }
        placesClient = Places.createClient(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
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

        // Enhanced map UI settings
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);

        // Set marker click listener
        mMap.setOnMarkerClickListener(marker -> {
            if (marker.getTag() != null && marker.getTag() instanceof Place) {
                showPlaceInfo((Place) marker.getTag(), marker);
                selectedMarker = marker;
                return true;
            }
            return false;
        });

        // Hide info card when map is clicked
        mMap.setOnMapClickListener(latLng -> hideInfoCard());

        getLocationPermission();
    }

    private void findNearbyPlaces(String query, BitmapDescriptor markerIcon, String displayName) {
        if (mMap == null || !locationPermissionGranted || lastKnownLocation == null) {
            hideLoading();
            Toast.makeText(this, "Cannot get location. Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        mMap.clear();
        hideInfoCard();

        LatLng currentLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());

        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.PHONE_NUMBER
        );

        CircularBounds bounds = CircularBounds.newInstance(currentLatLng, SEARCH_RADIUS_METERS);

        SearchByTextRequest request = SearchByTextRequest.builder(query, placeFields)
                .setLocationBias(bounds)
                .setMaxResultCount(20)
                .build();

        placesClient.searchByText(request).addOnCompleteListener(task -> {
            hideLoading();

            if (task.isSuccessful() && task.getResult() != null) {
                List<Place> places = task.getResult().getPlaces();
                placesFoundCount = places.size();

                Log.d(TAG, "Found " + places.size() + " places for query: " + query);

                if (places.isEmpty()) {
                    Toast.makeText(this, "No " + displayName.toLowerCase() + " found nearby.", Toast.LENGTH_SHORT).show();
                    return;
                }

                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                boundsBuilder.include(currentLatLng);

                for (Place place : places) {
                    if (place.getLatLng() == null) continue;

                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .title(place.getName())
                            .snippet(place.getAddress())
                            .position(place.getLatLng())
                            .icon(markerIcon)
                    );

                    if (marker != null) {
                        marker.setTag(place);
                    }

                    boundsBuilder.include(place.getLatLng());
                }

                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150));

                Toast.makeText(this, "Found " + places.size() + " " + displayName.toLowerCase(), Toast.LENGTH_SHORT).show();

            } else {
                Log.e(TAG, "Places search failed: " + task.getException());
                Toast.makeText(this, "Search failed. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPlaceInfo(Place place, Marker marker) {
        tvPlaceName.setText(place.getName());
        tvPlaceAddress.setText(place.getAddress() != null ? place.getAddress() : "Address not available");

        if (lastKnownLocation != null && place.getLatLng() != null) {
            float[] results = new float[1];
            Location.distanceBetween(
                    lastKnownLocation.getLatitude(),
                    lastKnownLocation.getLongitude(),
                    place.getLatLng().latitude,
                    place.getLatLng().longitude,
                    results
            );

            float distanceKm = results[0] / 1000;
            tvPlaceDistance.setText(String.format(Locale.getDefault(), "%.1f km away", distanceKm));
        } else {
            tvPlaceDistance.setText("Distance unavailable");
        }

        // Set icon color based on current search type
        int iconColor = getIconColorForSearchType();
        ivPlaceIcon.setColorFilter(ContextCompat.getColor(this, iconColor));

        btnGetDirections.setOnClickListener(v -> {
            // Open Google Maps for directions
            if (place.getLatLng() != null) {
                android.content.Intent intent = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("google.navigation:q=" +
                                place.getLatLng().latitude + "," + place.getLatLng().longitude)
                );
                intent.setPackage("com.google.android.apps.maps");
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Google Maps not installed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Animate info card in
        infoCard.setVisibility(View.VISIBLE);
        Animation slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left);
        infoCard.startAnimation(slideUp);

        // Animate camera to marker
        if (place.getLatLng() != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15));
        }
    }

    private void hideInfoCard() {
        if (infoCard.getVisibility() == View.VISIBLE) {
            Animation slideDown = AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right);
            slideDown.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    infoCard.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            infoCard.startAnimation(slideDown);
        }

        if (selectedMarker != null) {
            selectedMarker.hideInfoWindow();
            selectedMarker = null;
        }
    }

    private void showLoading(String message) {
        tvLoadingText.setText(message);
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.setAlpha(0f);
        loadingOverlay.animate().alpha(1f).setDuration(200).start();
    }

    private void hideLoading() {
        loadingOverlay.animate().alpha(0f).setDuration(200).withEndAction(() ->
                loadingOverlay.setVisibility(View.GONE)
        ).start();
    }

    private int getIconColorForSearchType() {
        switch (currentSearchType) {
            case "Bunks": return R.color.md_deep_orange_500;
            case "Repairs": return R.color.md_blue_600;
            case "Food": return R.color.md_green_600;
            case "ATMs": return R.color.md_teal_600;
            case "Hospitals": return R.color.md_red_600;
            case "Parking": return R.color.md_purple_600;
            default: return R.color.black;
        }
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(5000)
                .build();
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location newLocation = locationResult.getLastLocation();
                if (newLocation == null) return;

                if (lastKnownLocation == null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(newLocation.getLatitude(), newLocation.getLongitude()), 14));
                }
                lastKnownLocation = newLocation;
                stopLocationUpdates();
            }
        };
    }

    private void getLocationPermission() {
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

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
                Toast.makeText(this, "Location permission is required to find places.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void getDeviceLocation() {
        if (locationPermissionGranted) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);

            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());

            fusedLocationProviderClient.getLastLocation().addOnCompleteListener(this, task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    if (lastKnownLocation == null) {
                        lastKnownLocation = task.getResult();
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()), 14));
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    private BitmapDescriptor bitmapDescriptorFromVector(Context c, int vectorResId, int colorResId) {
        Drawable d = ContextCompat.getDrawable(c, vectorResId);
        if (d == null) return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            d.setTint(ContextCompat.getColor(c, colorResId));
        }

        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        Bitmap b = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas can = new Canvas(b);
        d.draw(can);
        return BitmapDescriptorFactory.fromBitmap(b);
    }
}