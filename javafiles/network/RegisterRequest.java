package com.example.findingbunks_part2.network;

public class RegisterRequest {
    public String car_id;
    public String dongle_mac;
    public String android_id;
    public String mode; // "manual" or "obd"
    public String public_key;
    public String user_name; // <-- NEW: Add user_name

    public RegisterRequest(String car_id, String dongle_mac, String android_id, String mode, String public_key, String user_name) {
        this.car_id = car_id;
        this.dongle_mac = dongle_mac;
        this.android_id = android_id;
        this.mode = mode;
        this.public_key = public_key;
        this.user_name = user_name; // <-- NEW: Assign user_name
    }
}