package com.example.findingbunks_part2;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        // Initialize UI elements
        ImageView ivProfile = findViewById(R.id.ivProfile);
        ImageView ivMenu = findViewById(R.id.ivMenu);
        CardView cardLiveTracking = findViewById(R.id.cardLiveTracking);
        CardView cardVehicleHealth = findViewById(R.id.cardVehicleHealth);

        // --- NEW: Find new cards ---
        CardView cardExploreNearby = findViewById(R.id.cardExploreNearby);
        CardView cardTripHistory = findViewById(R.id.cardTripHistory);
        // --- END NEW ---

        // Click listener for Live Tracking card
        cardLiveTracking.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, FetchIdsActivity.class);
            startActivity(intent);
        });

        // Click listener for Vehicle Health card (dummy)
        cardVehicleHealth.setOnClickListener(v -> {
            Toast.makeText(DashboardActivity.this, "Vehicle Health feature coming soon!", Toast.LENGTH_SHORT).show();
        });

        // --- NEW: Click listeners for new cards ---

        // Click listener for Explore Nearby
        cardExploreNearby.setOnClickListener(v -> {
            // Start the new ExploreActivity
            Intent intent = new Intent(DashboardActivity.this, ExploreActivity.class);
            startActivity(intent);
        });

        // Click listener for Trip History (dummy)
        cardTripHistory.setOnClickListener(v -> {
            Toast.makeText(DashboardActivity.this, "Trip History feature coming soon!", Toast.LENGTH_SHORT).show();
        });

        // --- END NEW ---


        // Click listener for the profile icon (dummy)
        ivProfile.setOnClickListener(v -> {
            Toast.makeText(DashboardActivity.this, "Profile section coming soon!", Toast.LENGTH_SHORT).show();
        });

        // Click listener for the menu icon
        ivMenu.setOnClickListener(this::showPopupMenu);
    }

    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.getMenuInflater().inflate(R.menu.dashboard_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.menu_account) {
                    Toast.makeText(DashboardActivity.this, "Account Settings", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.menu_live_tracking) {
                    Intent intent = new Intent(DashboardActivity.this, FetchIdsActivity.class);
                    startActivity(intent);
                    return true;
                } else if (id == R.id.menu_connect_obd) {
                    Toast.makeText(DashboardActivity.this, "Connect to OBD (via Live Tracking)", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        });
        popupMenu.show();
    }
}