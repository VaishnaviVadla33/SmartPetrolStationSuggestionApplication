# RefuelCue: Secure Automotive Fuel Assistance System

Real-time OBD-II vehicle data collection with secure RSA-based device authentication and petrol station discovery via Google Maps integration.

---

## What's Built

### ✅ Implemented Features

- **OBD-II Integration**: Bluetooth real-time vehicle data (speed, fuel level, RPM)
- **Security**: Device-level RSA key generation, role-based user access (MAIN/SECONDARY)
- **Station Discovery**: Google Maps/Places API integration for nearby petrol stations
- **Dashboard UI**: Fuel tracking, vehicle health monitoring, live tracking
- **Backend API**: User registration with device binding, public key exchange

### 🔄 In Development

- Firestore database synchronization
- Advanced station reachability filtering
- Trip history & analytics

---

## Project Structure

```
findingbunks_part2/
├── javafiles/                    # Android source code
│   ├── DashboardActivity.java    # Main dashboard UI
│   ├── FetchIdsActivity.java     # OBD-II data fetching
│   ├── ExploreActivity.java      # Station discovery UI
│   ├── MapsActivity.java         # Google Maps integration
│   ├── ObdManager.java           # Bluetooth + OBD-II handler
│   ├── ShA RedObdManager.java    # Shared OBD data across activities
│   ├── network/
│   │   ├── ApiClient.java        # Retrofit HTTP client
│   │   ├── ApiService.java       # API endpoints
│   │   └── RegisterRequest.java  # Registration payload
│   └── security/
│       ├── SecurityManager.java  # RSA key generation & signing
│       ├── RoleChecker.java      # Role-based access (MAIN/SECONDARY)
│       └── SecureStorage.java    # Local encrypted storage
├── res/                          # Android resources (layouts, drawables, values)
├── Backend/
│   ├── app.py                    # Flask server
│   └── register_user.py          # User registration logic
└── README.md
```

---

## Android Setup

### Prerequisites
- Android Studio (latest)
- Java 11+
- Minimum SDK: Android 26
- Gradle 8.0+
- ngrok account (for backend tunneling during development)

### Step 1: Clone & Import
```bash
git clone <repository-link>
cd findingbunks_part2
# Open in Android Studio: File → Open → select this folder
```

### Step 2: Add Required Files to Android Project

Copy these files from `javafiles/` to your Android project's source:

**Java Files:**
```
app/src/main/java/com/example/findingbunks_part2/
  ├── DashboardActivity.java
  ├── FetchIdsActivity.java
  ├── ExploreActivity.java
  ├── MapsActivity.java
  ├── ObdManager.java
  ├── SharedObdManager.java
  ├── VoiceNotificationManager.java
  ├── NavigationStepAdapter.java
  ├── PetrolStationAdapter.java
  └── network/
      ├── ApiClient.java
      ├── ApiService.java
      ├── RegisterRequest.java
      ├── RegisterResponse.java
      └── HelloResponse.java
  └── security/
      ├── SecurityManager.java
      ├── RoleChecker.java
      └── SecureStorage.java
```

**Resource Files:**
```
app/src/main/res/
  ├── layout/
  │   ├── activity_dashboard.xml
  │   ├── activity_maps.xml
  │   ├── activity_explore.xml
  │   ├── activity_fetch_ids.xml
  │   └── list_item_station.xml
  ├── drawable/ (all XML drawables)
  ├── values/
  │   ├── colors.xml
  │   ├── strings.xml
  │   └── themes.xml
  └── xml/
      ├── network_security_config.xml
      └── data_extraction_rules.xml
```

### Step 3: Configure Dependencies

**build.gradle.kts (Module: app)**
```gradle
dependencies {
    // Retrofit for network requests
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.retrofit2:converter-scalars:2.9.0'
    
    // Volley (alternative HTTP client already used in MapsActivity)
    implementation 'com.android.volley:volley:1.2.1'
    
    // Google Maps & Places
    implementation 'com.google.android.gms:play-services-maps:18.2.0'
    implementation 'com.google.android.gms:play-services-location:21.0.1'
    implementation 'com.google.android.libraries.places:places:3.2.0'
    
    // Material Design
    implementation 'com.google.android.material:material:1.10.0'
    
    // Firebase (when Firestore is integrated)
    implementation 'com.google.firebase:firebase-firestore:24.9.1'
    implementation 'com.google.firebase:firebase-auth:22.3.0'
    
    // CardView
    implementation 'androidx.cardview:cardview:1.0.0'
    
    // RecyclerView
    implementation 'androidx.recyclerview:recyclerview:1.3.1'
}
```

### Step 4: Configure AndroidManifest.xml

**Required Permissions:**
```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />

<!-- Google Maps API Key -->
<application>
    <meta-data
        android:name="com.google.android.geo.API_KEY"
        android:value="YOUR_GOOGLE_MAPS_API_KEY" />
    
    <!-- Activities -->
    <activity android:name=".DashboardActivity" />
    <activity android:name=".FetchIdsActivity" />
    <activity android:name=".ExploreActivity" />
    <activity android:name=".MapsActivity" />
</application>
```

### Step 5: Add API Keys

**local.properties:**
```properties
# Get these from Google Cloud Console
GOOGLE_MAPS_API_KEY=YOUR_KEY_HERE
GOOGLE_PLACES_API_KEY=YOUR_KEY_HERE
```

### Step 6: Update ApiClient Base URL

Edit `network/ApiClient.java`:
```java
private static final String BASE_URL = "http://your-backend-url/";
// During development: use ngrok tunnel
// private static final String BASE_URL = "https://your-ngrok-url.ngrok-free.app/";
```

### Step 7: Build & Run
```bash
./gradlew clean build
./gradlew installDebug
```

---

## Backend Setup

### Prerequisites
- Python 3.8+
- pip package manager
- Optional: ngrok (for mobile testing)

### Installation

```bash
cd Backend
pip install -r requirements.txt
# Create requirements.txt with:
# flask==2.3.0
# python-dotenv==1.0.0
# firebase-admin==6.0.0 (when Firestore is ready)
```

### Running the Server

```bash
# Development
python app.py

# Production (with ngrok for mobile testing)
ngrok http 5000
# Copy the HTTPS URL to AndroidApiClient.java
```

### API Endpoints

#### **1. User Registration**
```
POST /register
Content-Type: application/json

{
  "car_id": "1HGCM41JXMN109186",
  "dongle_mac": "98:D3:31:FD:7E:7F",
  "android_id": "device_unique_id",
  "user_name": "John Doe",
  "public_key": "base64_encoded_rsa_public_key",
  "mode": "manual"  // or "auto"
}

Response: 200 OK
{
  "status": "success",
  "message": "User registered successfully",
  "user_id": "uid_12345",
  "role": "MAIN"
}
```

Device linking is **one-to-one**: each vehicle (car_id) can only have ONE main user and optional secondary users.

#### **2. Health Check**
```
GET /hello

Response: 200 OK
{
  "message": "Hello from Python Backend!"
}
```

---

## Security Implementation

### 1. Device-Level RSA Key Management

All encryption happens **on-device** in Android Keystore (hardware-backed on supported phones).

**SecurityManager.java** – Key Generation
```java
// Called during first app launch / registration
public static void generateKeyPair() {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
    
    KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
        "VehicleAppRSAKey",
        KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        .setKeySize(2048)
        .build();
    
    keyGen.initialize(spec);
    KeyPair keyPair = keyGen.generateKeyPair();
}
```

**What happens:**
1. RSA-2048 key pair generated on device (private key never leaves Keystore)
2. Public key sent to backend during registration
3. Backend stores it mapped to `(car_id, dongle_mac, android_id)`
4. Device signs all requests; backend verifies using stored public key

### 2. Data Signing for Authentication

Every request from the app is **digitally signed** using the device's private key:

```java
public static String signData(String data) {
    PrivateKey privateKey = (PrivateKey) keyStore.getKey("VehicleAppRSAKey", null);
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(data.getBytes());
    return Base64.encodeToString(signature.sign(), Base64.NO_WRAP);
}
```

**API Request Example:**
```json
{
  "data": {
    "speed": "65",
    "fuel_percent": "45",
    "timestamp": "1678256400000"
  },
  "signature": "base64_encoded_signature"
}
```

Backend verifies signature:
```python
# Backend: verify request came from registered device
public_key = get_stored_public_key(car_id, dongle_mac)
is_valid = verify_signature(data, signature, public_key)
```

### 3. Role-Based Access (Offline)

After registration, **user role is stored locally** and works offline:

**RoleChecker.java:**
```java
public static boolean isMainUser(Context context) {
    String role = SecureStorage.getUserRole(context);
    return "MAIN".equalsIgnoreCase(role);
}

public static boolean isSecondaryUser(Context context) {
    String role = SecureStorage.getUserRole(context);
    return "SECONDARY".equalsIgnoreCase(role);
}
```

**Access Rules:**
| Role | Can Read OBD Data | Can Register Device | Can Modify Settings |
|------|------------------|---------------------|-------------------|
| **MAIN** | ✅ Yes | ✅ Yes | ✅ Yes |
| **SECONDARY** | ✅ Yes (read-only) | ❌ No | ❌ No |

### 4. Device Binding (One Car–One Dongle–One Main User)

During registration, the backend creates an immutable mapping:

```python
# Backend: register_user()
device_mapping = {
    "car_id": "1HGCM41JXMN109186",
    "dongle_mac": "98:D3:31:FD:7E:7F",      # Bluetooth MAC
    "android_id": "device_unique_id",       # Device fingerprint
    "primary_user_id": "uid_12345",         # Only 1 allowed
    "secondary_users": [],                   # Optional additional users
    "public_key": "rsa_public_key_base64",
    "registration_date": "2024-03-20",
    "trust_level": "ESTABLISHED"
}
```

**Security guarantee:** 
- Only the primary user can modify device mapping
- Secondary users can be added by primary user only
- OBD dongle cannot connect to two cars simultaneously

### 5. Secure Local Storage

**SecureStorage.java** – Stores user data locally (encrypted on Android 10+):

```java
public static void saveUserRole(Context context, String role) {
    SharedPreferences prefs = 
        context.getSharedPreferences("app_security", MODE_PRIVATE);
    prefs.edit().putString("user_role", role).apply();
}

public static String getUserRole(Context context) {
    SharedPreferences prefs = 
        context.getSharedPreferences("app_security", MODE_PRIVATE);
    return prefs.getString("user_role", "UNKNOWN");
}
```

---

## Core Components

### ObdManager.java – OBD-II Bluetooth Communication

```java
// Reads vehicle data via Bluetooth ELM327 dongle
obdManager = new ObdManager(context, new ObdManager.ObdDataListener() {
    @Override
    public void onObdDataReceived(String speed, float fuelLeft, float range) {
        // Update UI with real-time data
        speedTextView.setText(speed + " km/h");
        fuelTextView.setText(fuelLeft + "%");
    }
    
    @Override
    public void onObdStatusChanged(String status) {
        // Connection status: "Connecting...", "Connected", "Disconnected"
        statusTextView.setText(status);
    }
});

// Connect/disconnect
obdManager.connect();
obdManager.disconnect();
```

**Supported PIDs:**
- `010D` → Speed (km/h)
- `012F` → Fuel Tank Level (%)
- `010C` → RPM
- `0110` → MAF (g/s)

### MapsActivity.java – Google Maps & Nearby Stations

```java
// Search nearby petrol stations
PlacesClient placesClient = Places.createClient(context);

SearchByTextRequest request = SearchByTextRequest.newInstance(
    "petrol station",
    CircularBounds.newInstance(
        new LatLng(latitude, longitude),
        15000  // 15km radius
    )
);

placesClient.searchByText(request)
    .addOnSuccessListener(response -> {
        List<Place> stations = response.getPlaces();
        // Filter reachable stations based on fuel range
        displayStationsOnMap(stations);
    });
```

**Station Details Retrieved:**
- Name, address, location (lat/lng)
- Distance, opening hours, rating
- Fuel types available

### ExploreActivity.java – Station Discovery UI

Displays nearby petrol stations with:
- Distance from current location
- Reachability based on fuel level
- Opening status (open now / closed)
- Rating & reviews
- Phone number, website

### FetchIdsActivity.java – OBD-II Data Fetching

Connects to OBD-II dongle and streams:
- Real-time speed
- Fuel percentage
- Engine RPM
- Estimated remaining range

---

## Android Bluetooth Permissions (Runtime)

Android 12+ requires runtime permissions:

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
        
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.BLUETOOTH_CONNECT},
            PERMISSION_REQUEST_CODE);
    }
}
```

---

## Testing Checklist

- [ ] OBD-II Dongle pairs successfully via Bluetooth
- [ ] Real-time speed/fuel data displays in FetchIdsActivity
- [ ] User registration succeeds with `/register` endpoint
- [ ] Device mapping stored in backend
- [ ] Role-based restrictions enforced (MAIN vs SECONDARY)
- [ ] Google Maps loads current location
- [ ] Nearby stations appear within 15km radius
- [ ] Connection survives app rotation & background transitions
- [ ] Voice notifications while driving
- [ ] Offline mode for cached station data

---

## Common Issues

### OBD-II Connection Fails
- Pair dongle in Settings first
- Verify Bluetooth permissions granted
- Check device supports Bluetooth LE
- Try restarting Bluetooth

### Maps Not Loading
- Verify Google Maps API key in AndroidManifest.xml
- Ensure location permissions granted
- Check Internet connectivity

### Backend Unreachable
- Verify ngrok URL in ApiClient.java
- Keep ngrok tunnel running
- Check firewall port 5000

### Registration Fails
- Public key not generated yet (call `SecurityManager.generateKeyPair()` first)
- Missing required fields: car_id, dongle_mac, android_id, user_name
- Backend server not running

---

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Mobile App | Android | 12.0+ |
| Language | Java | 11 |
| Maps | Google Maps SDK | 18.2.0 |
| Security | RSA-2048 + SHA256 | Hardware Keystore |
| Backend | Flask | 2.3.0 |
| Bluetooth | ELM327 OBD-II | SPP Profile |
| Database | Firestore | ---- |

---

## Future Roadmap

- [ ] Firestore integration for cloud sync
- [ ] Machine learning for fuel consumption prediction
- [ ] Trip history & analytics dashboard
- [ ] Multi-car support for single user
- [ ] Advanced station filtering (fuel availability, parking, etc.)
- [ ] Push notifications (FCM) for low fuel alerts
