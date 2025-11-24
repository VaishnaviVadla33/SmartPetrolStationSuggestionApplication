package com.example.findingbunks_part2;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class NavigationStepAdapter extends RecyclerView.Adapter<NavigationStepAdapter.StepViewHolder> {

    private List<String> steps;

    public NavigationStepAdapter(List<String> steps) {
        this.steps = steps;
    }

    @NonNull
    @Override
    public StepViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new StepViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
        String stepHtml = steps.get(position);
        // Use Html.fromHtml to remove tags like <b>, <div>
        holder.textView.setText((position + 1) + ". " + Html.fromHtml(stepHtml, Html.FROM_HTML_MODE_LEGACY));
    }

    @Override
    public int getItemCount() {
        return steps.size();
    }

    static class StepViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public StepViewHolder(@NonNull View itemView) {
            super(itemView);
            // simple_list_item_1 just has one TextView with id "text1"
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}