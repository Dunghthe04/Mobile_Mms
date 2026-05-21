package com.mkac.meikomms.ui.home;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.mkac.meikomms.R;
import com.mkac.meikomms.ui.taskdetail.ImageAdapter;
import com.mkac.meikomms.ui.taskdetail.ImageModel;

import java.util.ArrayList;
import java.util.List;

public class ImageNewErrorAdapter extends RecyclerView.Adapter<ImageNewErrorAdapter.ViewHolder>
{
    private List<ImageModel> mimageList;
    ArrayList<Uri> mselectedFileUris;
    private Context mcontext;

    public ImageNewErrorAdapter(List<ImageModel> imageList,ArrayList<Uri> selectedFileUris ,Context context) {
        this.mimageList = imageList;
        this.mselectedFileUris = selectedFileUris;
        this.mcontext = context;
    }

    @NonNull
    @Override
    public ImageNewErrorAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mcontext).inflate(R.layout.item_image, parent, false);
        return new ImageNewErrorAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageNewErrorAdapter.ViewHolder holder, int position) {
        Uri imageUri = mimageList.get(position).getImageUri();
        // holder.imageView.setImageURI(imageUri);
        if(imageUri != null)
        {
            mselectedFileUris.add(imageUri);
        }
        holder.progressBar.setVisibility(View.VISIBLE);
        // Load ảnh bằng Glide
        Glide.with(mcontext)
                .load(imageUri)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                        holder.progressBar.setVisibility(View.GONE);
                        // ... xử lý lỗi
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                        holder.progressBar.setVisibility(View.GONE);
                        // ... xử lý thành công
                        return false;
                    }
                })
                .placeholder(R.drawable.image_24) // Ảnh tạm trong khi tải
                .error(R.drawable.no_image_gray_24) // Ảnh hiển thị nếu lỗi
                .into(holder.imageView);


        holder.btn_delete_image.setOnClickListener(v -> {

            mimageList.remove(position);
            mselectedFileUris.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, mimageList.size());
        });


    }

    @Override
    public int getItemCount() {
        return mimageList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView,btn_delete_image;
        ProgressBar progressBar;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewItem);
            progressBar = itemView.findViewById(R.id.progressBar);
            btn_delete_image = itemView.findViewById(R.id.btn_delete_image);
        }
    }
}
