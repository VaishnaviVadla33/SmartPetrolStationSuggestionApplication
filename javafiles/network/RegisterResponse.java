package com.example.findingbunks_part2.network;

public class RegisterResponse {
    public String status;  // "success" / "error"
    public String role;    // "MAIN" / "SECONDARY"
    public String message; // human-readable
    public String mode;    // echoed/stored mode
}
