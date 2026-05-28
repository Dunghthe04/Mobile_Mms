package com.mkac.meikomms.ui.workorder;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.mkac.meikomms.common.HttpClient;
import com.mkac.meikomms.R;

import java.util.ArrayList;
import java.util.List;

public class MaintenanceImagePreviewAdapter extends RecyclerView.Adapter<MaintenanceImagePreviewAdapter.ViewHolder> {
    private final Context context;
    private final List<String> imagePaths = new ArrayList<>();

    public MaintenanceImagePreviewAdapter(Context context) {
        this.context = context;
    }

    public void submitImages(List<String> images) {
        imagePaths.clear();
        if (images != null) {
            for (String image : images) {
                if (image != null) {
                    String cleaned = image.trim();
                    if (!cleaned.isEmpty() && !imagePaths.contains(cleaned)) {
                        imagePaths.add(cleaned);
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_maintenance_image_preview, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String imagePath = imagePaths.get(position);
        Log.d("PREVIEW_LOAD_DBG", "attempt load preview pos=" + position + " url=" + imagePath);
        Object loadModel = imagePath;
        if (imagePath != null && (imagePath.startsWith("http://") || imagePath.startsWith("https://"))) {
            String token = HttpClient.getToken();
            if (token != null && !token.trim().isEmpty()) {
            GlideUrl gUrl = new GlideUrl(imagePath, new LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer " + token)
                .build());
            loadModel = gUrl;
            }
        }
        Glide.with(holder.itemView)
            .load(loadModel)
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                        Log.e("PREVIEW_LOAD_DBG", "onLoadFailed url=" + String.valueOf(model), e);
                        // Try a raw fetch with Authorization header to get HTTP status / content-type for debugging
                        final String urlToDebug = imagePath;
                        new Thread(() -> {
                            try {
                                com.mkac.meikomms.common.HttpClient.APIReturn r = HttpClient.fetchUrlDebug(urlToDebug);
                                Log.d("PREVIEW_DEBUG", "fetchUrlDebug result code=" + (r == null ? "null" : r.code));
                            } catch (Exception ex) {
                                Log.e("PREVIEW_DEBUG", "fetchUrlDebug exception", ex);
                            }
                        }).start();
                        return false; // allow error placeholder to be set
                    }

                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        Log.d("PREVIEW_LOAD_DBG", "onResourceReady url=" + String.valueOf(model));
                        return false;
                    }
                })
                .placeholder(R.drawable.image_24)
                .error(R.drawable.no_image_gray_24)
                .centerCrop()
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return imagePaths.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.img_preview);
        }
    }
}