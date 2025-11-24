import time
import firebase_admin
from firebase_admin import credentials, firestore

# Load Firebase Service Key
SERVICE_KEY_PATH = r"D:\CapstoneProject\Codes\Backend\firebase_service_key.json"

if not firebase_admin._apps:
    cred = credentials.Certificate(SERVICE_KEY_PATH)
    firebase_admin.initialize_app(cred)

db = firestore.client()

def register_user(car_id: str, dongle_mac: str, android_id: str, mode: str, public_key: str, user_name: str):
    """
    Register a user to a vehicle using a vehicle-centric database model.
    
    - Creates one document per vehicle in the 'vehicles' collection.
    - The first user to register the vehicle becomes the 'main_user'.
    - All subsequent users are added to the 'secondary_users' array.
    """
    
    # Use a new collection called 'vehicles'
    vehicle_ref = db.collection("vehicles").document(car_id)
    vehicle_doc = vehicle_ref.get()
    
    # --- FIX for TypeError ---
    # Use SERVER_TIMESTAMP for top-level fields
    server_time = firestore.SERVER_TIMESTAMP 
    # Use a Python timestamp (int) for nested fields
    user_register_time = int(time.time())
    # --- END FIX ---
    
    # This is the data we will store for this specific user
    user_payload = {
        "android_id": android_id,
        "user_name": user_name,
        "public_key": public_key,
        "registered_at": user_register_time  # <-- Use the Python time here
    }

    role = "SECONDARY"  # Default to secondary
    message = "User already registered."

    if not vehicle_doc.exists:
        # --- CASE 1: New Vehicle (First user registers) ---
        # This user becomes the MAIN user
        role = "MAIN"
        message = f"New vehicle registered. '{user_name}' is now the MAIN user."
        
        new_vehicle_data = {
            "dongle_mac": dongle_mac,
            "main_user": user_payload,
            "secondary_users": [],  # Empty list for secondary users
            "created_at": server_time,  # <-- Use server_time here (top level)
            "last_updated": server_time # <-- Use server_time here (top level)
        }
        
        vehicle_ref.set(new_vehicle_data)
        
        print(f"✅ New vehicle registered: {car_id}. User: {user_name} ({android_id}) as {role}")

    else:
        # --- CASE 2: Vehicle Already Exists ---
        vehicle_data = vehicle_doc.to_dict()
        main_user = vehicle_data.get("main_user", {})
        
        # Check if this user is the MAIN user
        if main_user.get("android_id") == android_id:
            role = "MAIN"
            message = f"Welcome back, {user_name}. (MAIN user)"
            
            # --- FIX: Main user can update the dongle_mac ---
            updates = {
                "main_user": user_payload,
                "dongle_mac": dongle_mac,  # <-- Allow MAIN user to update dongle
                "last_updated": server_time
            }
            vehicle_ref.update(updates)
            
            print(f"✅ MAIN user updated: {user_name} ({android_id}) for {car_id}")

        else:
            # Check if this user is already a SECONDARY user
            secondary_users = vehicle_data.get("secondary_users", [])
            existing_user_index = -1
            
            for i, user in enumerate(secondary_users):
                if user.get("android_id") == android_id:
                    existing_user_index = i
                    break
            
            if existing_user_index != -1:
                # User is already a secondary user, update their info
                role = "SECONDARY"
                message = f"Welcome back, {user_name}. (SECONDARY user)"
                
                # Update the user's info in the array
                secondary_users[existing_user_index] = user_payload
                vehicle_ref.update({
                    "secondary_users": secondary_users,
                    "last_updated": server_time 
                })
                
                print(f"✅ SECONDARY user updated: {user_name} ({android_id}) for {car_id}")
            
            else:
                # This is a new SECONDARY user
                # We check if the dongle_mac matches before allowing a new user
                if vehicle_data.get("dongle_mac") != dongle_mac:
                    print(f"❌ REJECTED: Secondary user {user_name} ({android_id}) tried to register with wrong dongle MAC for {car_id}")
                    return {
                        "status": "error",
                        "role": "NONE",
                        "message": "Registration failed. The dongle MAC does not match the MAIN user's dongle."
                    }, 403 # 403 Forbidden
                
                role = "SECONDARY"
                message = f"'{user_name}' successfully added as a SECONDARY user."
                
                # Add the new user to the array of secondary users
                vehicle_ref.update({
                    "secondary_users": firestore.ArrayUnion([user_payload]),
                    "last_updated": server_time 
                })
                
                print(f"✅ New SECONDARY user added: {user_name} ({android_id}) to {car_id}")

    return {
        "status": "success", 
        "role": role, 
        "message": message,
        "mode": mode
    }