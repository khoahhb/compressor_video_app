package com.example.compress_video_app.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.compress_video_app.R;
import com.example.compress_video_app.compressor.HandleVideo;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.util.ArrayList;

public class CompressedVideosAdapter extends RecyclerView.Adapter<CompressedVideosAdapter.ViewHolder> {

    private static final String TAG = "VideoFilesAdapter2";
    private final Context context;
    BottomSheetDialog bottomSheetDialog;
    private ArrayList<HandleVideo> videoList;
    private final CompressedVideoListener compressedVideoListener;

    public CompressedVideosAdapter(ArrayList<HandleVideo> videoList, Context context, CompressedVideoListener compressedVideoListener) {
        this.videoList = videoList;
        this.context = context;
        this.compressedVideoListener = compressedVideoListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.video_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {

        holder.videoName.setText(videoList.get(position).getFormatFileName());
        holder.videoSize.setText(videoList.get(position).getFormatSize(context));
        holder.videoDuration.setText(videoList.get(position).getFormatDuration());

        Glide.with(context).load(new File(videoList.get(position).getUri().getPath()))
                .into(holder.thumbnail);

        holder.menu_more.setVisibility(View.GONE);
        holder.videoName.setTextColor(Color.WHITE);
        holder.videoSize.setTextColor(Color.WHITE);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                compressedVideoListener.onClickHandle(videoList.get(position).getUri());
            }
        });
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public void updateVideoFiles(ArrayList<HandleVideo> files) {
        videoList = new ArrayList<>();
        videoList.addAll(files);
        notifyDataSetChanged();
    }

    public interface CompressedVideoListener {
        void onClickHandle(Uri uri);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail, menu_more;
        TextView videoName, videoSize, videoDuration;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            menu_more = itemView.findViewById(R.id.video_menu_more);
            videoName = itemView.findViewById(R.id.video_name);
            videoSize = itemView.findViewById(R.id.video_size);
            videoDuration = itemView.findViewById(R.id.video_duration);
        }
    }
}