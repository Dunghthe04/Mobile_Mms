package com.mkac.meikomms.ui.taskdetail;

import android.app.DownloadManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
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
import com.mkac.meikomms.common.TimeUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
    private List<ImageModel> imageList;
    ArrayList<Uri> mselectedFileUris;
    ArrayList<String> urisdelete = new ArrayList<>();
    private String mTask_Id;
    private Context context;

    public ImageAdapter(Context context, List<ImageModel> imageList, ArrayList<Uri> selectedFileUris,String Task_Id)
    {
        this.context = context;
        this.mselectedFileUris = selectedFileUris;
        this.mTask_Id = Task_Id;
        this.imageList = imageList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Uri imageUri = imageList.get(position).getImageUri();
        // holder.imageView.setImageURI(imageUri);
        if(imageUri != null)
        {
            mselectedFileUris.add(imageUri);
        }
        holder.progressBar.setVisibility(View.VISIBLE);
        // Load ảnh bằng Glide
        Glide.with(context)
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
            String filepath = imageList.get(position).getImageUri().toString();
            if(filepath.contains("http"))
            {
                String[] file_arr = filepath.split("/");
                urisdelete.add(file_arr[file_arr.length-1]);
            }
            imageList.remove(position);
            mselectedFileUris.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, imageList.size());
        });


    }

    public String getlistFileDel()
    {
        return String.join(",",urisdelete);
    }


    public String getmselectedFileUris()
    {
        ArrayList<String> uris = new ArrayList<>();

        String file_no = "0";
        for (int i = 0; i < imageList.size(); i++)
        {
            String filepath = imageList.get(i).getImageUri().toString();
            if(filepath.contains("http"))
            {
                String[] file_arr = filepath.split("/");
                uris.add(file_arr[file_arr.length-1]);
                file_no = (file_arr[file_arr.length-1].substring(file_arr[file_arr.length-1].lastIndexOf("_")+1)).split("\\.")[0];
            }
            else
            {
                String file_name = mTask_Id+"_"+ TimeUtils.getCurrentUnixTimestamp()+"_"+ String.valueOf(Integer.valueOf(file_no)+1)+".jpg";
                uris.add(file_name);
                file_no = String.valueOf(Integer.valueOf(file_no)+1);
            }
        }
        return  String.join(",",uris);
    }

    public String getMimeTypeFromUri(Context context, Uri uri)
    {
        return context.getContentResolver().getType(uri).split("/")[1];
    }

    public String getMimeType(String filePath)
    {
        String extension = MimeTypeMap.getFileExtensionFromUrl(filePath);
        if (extension != null) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return imageList.size();
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