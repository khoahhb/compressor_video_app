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
import com.example.compress_video_app.models.HandleVideo;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;

public class CompressedVideosAdapter extends RecyclerView.Adapter<CompressedVideosAdapter.ViewHolder> {

    private static final String TAG = "VideoFilesAdapter2";
    private static final int SINGLE_LIST = 1;
    private static final int ALL_LIST = 2;

    private final Context context;
    private final CompressedVideoListener compressedVideoListener;
    private final int viewType;
    BottomSheetDialog bottomSheetDialog;
    private ArrayList<HandleVideo> videoList;

    public CompressedVideosAdapter(ArrayList<HandleVideo> videoList, Context context, int viewType, CompressedVideoListener compressedVideoListener) {
        this.videoList = videoList;
        this.context = context;
        this.viewType = viewType;
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

        holder.videoName.setText(videoList.get(position).getName());
        holder.videoSize.setText(videoList.get(position).getFormatSize(context));
        holder.videoDuration.setText(videoList.get(position).getFormatDuration());

        Glide.with(context).load(new File(videoList.get(position).getUri().getPath()))
                .into(holder.thumbnail);
        if (viewType == SINGLE_LIST) {
            holder.menu_more.setVisibility(View.GONE);
            holder.videoName.setTextColor(Color.WHITE);
            holder.videoSize.setTextColor(Color.WHITE);
        } else {
            holder.menu_more.setVisibility(View.VISIBLE);
            holder.menu_more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showInfoDialog(videoList.get(position));
                }
            });
            holder.videoName.setTextColor(Color.BLACK);
            holder.videoSize.setTextColor(Color.BLACK);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                compressedVideoListener.onClickHandle(videoList.get(position).getUri(), position);
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

    private void showInfoDialog(HandleVideo video) {

        String one = "File: " + video.getName();

        String two = "Size: " + video.getFormatSize(context);

        String three = "Resolution: " + video.getFormatResolution();

        String four = "Duration: " + video.getFormatDuration();

        String five = "Format: " + video.getMime();

        String six = "Codec: " + video.getCodec();
        String seven = "Bitrate: " + video.getFormatBitrate();
        String eight = "Frame rate: " + video.getFormatFrameRate();

        new MaterialAlertDialogBuilder(context,
                R.style.ThemeOverlay_Catalog_MaterialAlertDialog_Centered_FullWidthButtons)
                .setTitle("Properties")
                .setMessage(one + "\n\n" + two + "\n\n" + three + "\n\n" + four +
                        "\n\n" + five + "\n\n" + six + "\n\n" + seven + "\n\n" + eight)
                .setPositiveButton("OK", null)
                .show();
    }

    public interface CompressedVideoListener {
        void onClickHandle(Uri uri, int position);
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
