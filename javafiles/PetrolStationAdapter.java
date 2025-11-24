package com.example.findingbunks_part2; // Your package name

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//
// ------------------------------------------------------------------
// MAIN ADAPTER
// ------------------------------------------------------------------
//
public class PetrolStationAdapter extends RecyclerView.Adapter<PetrolStationAdapter.ViewHolder> {

    private List<PetrolStation> stations = new ArrayList<>();
    private OnStationClickListener listener;

    public interface OnStationClickListener {
        void onStationClick(PetrolStation station);
    }

    public void setOnStationClickListener(OnStationClickListener listener) {
        this.listener = listener;
    }

    public void setStations(List<PetrolStation> stations) {
        this.stations = stations;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_station, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PetrolStation station = stations.get(position);

        holder.tvName.setText(station.getName());
        holder.tvAddress.setText(station.getAddress());
        holder.tvDistance.setText(
                String.format(Locale.getDefault(), "%.1f km", station.getDistance())
        );

        // -------- Reachability Badge --------
        View parent = (View) holder.tvSafety.getParent();   // FIX: cast parent to View

        GradientDrawable bgDrawable = (GradientDrawable) parent.getBackground().mutate();

        if (station.isReachable()) {
            holder.tvSafety.setText("Safe");
            holder.tvSafety.setTextColor(Color.parseColor("#2E7D32"));
            holder.ivSafetyIcon.setImageResource(android.R.drawable.checkbox_on_background);
            holder.ivSafetyIcon.setColorFilter(Color.parseColor("#4CAF50"));

            bgDrawable.setColor(Color.parseColor("#E8F5E9"));

        } else {
            holder.tvSafety.setText("Risky");
            holder.tvSafety.setTextColor(Color.parseColor("#C62828"));
            holder.ivSafetyIcon.setImageResource(android.R.drawable.ic_dialog_alert);
            holder.ivSafetyIcon.setColorFilter(Color.parseColor("#F44336"));

            bgDrawable.setColor(Color.parseColor("#FFEBEE"));
        }


        // Click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onStationClick(station);
        });
    }

    @Override
    public int getItemCount() {
        return stations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress, tvDistance, tvSafety;
        ImageView ivSafetyIcon;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvStationName);
            tvAddress = itemView.findViewById(R.id.tvStationAddress);
            tvDistance = itemView.findViewById(R.id.tvStationDistance);
            tvSafety = itemView.findViewById(R.id.tvSafetyStatus);
            ivSafetyIcon = itemView.findViewById(R.id.ivSafetyIcon);
        }
    }
}

//
// ------------------------------------------------------------------
// PETROL STATION MODEL
// (package-private, allowed in same file)
// ------------------------------------------------------------------
//
class PetrolStation implements Comparable<PetrolStation> {

    private final String name;
    private final String address;
    private final float distanceInKm;
    private final LatLng location;
    private final boolean isReachable;

    public PetrolStation(String name, String address, float distanceKm, LatLng loc, boolean safe) {
        this.name = name;
        this.address = address;
        this.distanceInKm = distanceKm;
        this.location = loc;
        this.isReachable = safe;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }

    // -------- FIX: Add BOTH getters so MapsActivity and Adapter work --------
    public float getDistance() { return distanceInKm; }          // used in adapter & sorting
    public float getDistanceInKm() { return distanceInKm; }      // used in MapsActivity

    public LatLng getLocation() { return location; }
    public boolean isReachable() { return isReachable; }

    @Override
    public int compareTo(PetrolStation other) {
        return Float.compare(this.distanceInKm, other.distanceInKm);
    }
}
