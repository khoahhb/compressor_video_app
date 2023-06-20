package com.example.compress_video_app.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.compress_video_app.R;
import com.example.compress_video_app.activities.CompressActivity;
import com.example.compress_video_app.activities.CompressSingleActivity;
import com.example.compress_video_app.activities.VideoPlayerActivity;
import com.example.compress_video_app.models.MediaFiles;
import com.example.compress_video_app.models.Utility;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.util.ArrayList;

public class VideoFilesAdapter extends RecyclerView.Adapter<VideoFilesAdapter.ViewHolder> {

    private static final String TAG = "VideoFilesAdapter";
    private final Context context;
    private final int viewType;
    BottomSheetDialog bottomSheetDialog;
    private ArrayList<MediaFiles> videoList;

    public VideoFilesAdapter(ArrayList<MediaFiles> videoList, Context context, int viewType) {
        this.videoList = videoList;
        this.context = context;
        this.viewType = viewType;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.video_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {

        holder.videoName.setText(videoList.get(position).getDisplayName());
        String size = videoList.get(position).getSize();
        holder.videoSize.setText(android.text.format.Formatter.formatFileSize(context,
                Long.parseLong(size)));
        double milliSeconds = Double.parseDouble(videoList.get(position).getDuration());
        holder.videoDuration.setText(Utility.timeConversion((long) milliSeconds));

        Glide.with(context).load(new File(videoList.get(position).getPath()))
                .into(holder.thumbnail);

        if (viewType == 0) {
            holder.menu_more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bottomSheetDialog = new BottomSheetDialog(context, R.style.BottomSheetTheme);
                    View bsView = LayoutInflater.from(context).inflate(R.layout.video_bs_layout,
                            v.findViewById(R.id.bottom_sheet));
                    bsView.findViewById(R.id.bs_play).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            holder.itemView.performClick();
                            bottomSheetDialog.dismiss();
                        }
                    });
                    bsView.findViewById(R.id.bs_rename).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
                            alertDialog.setTitle("Rename to");
                            EditText editText = new EditText(context);
                            String path = videoList.get(position).getPath();
                            final File file = new File(path);
                            String videoName = file.getName();
                            videoName = videoName.substring(0, videoName.lastIndexOf("."));
                            editText.setText(videoName);
                            alertDialog.setView(editText);
                            editText.requestFocus();

                            alertDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (TextUtils.isEmpty(editText.getText().toString())) {
                                        Toast.makeText(context, "Can't rename empty file", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    String onlyPath = file.getParentFile().getAbsolutePath();
                                    String ext = file.getAbsolutePath();
                                    ext = ext.substring(ext.lastIndexOf("."));
                                    String newPath = onlyPath + "/" + editText.getText().toString() + ext;
                                    File newFile = new File(newPath);
                                    boolean rename = file.renameTo(newFile);
                                    if (rename) {
                                        ContentResolver resolver = context.getApplicationContext().getContentResolver();
                                        resolver.delete(MediaStore.Files.getContentUri("external"),
                                                MediaStore.MediaColumns.DATA + "=?", new String[]
                                                        {file.getAbsolutePath()});
                                        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                        intent.setData(Uri.fromFile(newFile));
                                        context.getApplicationContext().sendBroadcast(intent);

                                        notifyDataSetChanged();
                                        Toast.makeText(context, "Video Renamed", Toast.LENGTH_SHORT).show();

                                        SystemClock.sleep(200);
                                        ((Activity) context).recreate();
                                    } else {
                                        Toast.makeText(context, "Process Failed", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                            alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            alertDialog.create().show();
                            bottomSheetDialog.dismiss();
                        }
                    });

                    bsView.findViewById(R.id.bs_share).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {

                            Uri uri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", new File(videoList.get(position).getPath()));

                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            shareIntent.setType("video/*");
                            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                            context.startActivity(Intent.createChooser(shareIntent, "Share Video via"));
                            bottomSheetDialog.dismiss();
                        }
                    });

                    bsView.findViewById(R.id.bs_delete).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
                            alertDialog.setTitle("Delete");
                            alertDialog.setMessage("Do you want to delete this video");
                            alertDialog.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Uri contentUri = ContentUris
                                            .withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                                    Long.parseLong(videoList.get(position).getId()));
                                    File file = new File(videoList.get(position).getPath());
                                    boolean delete = file.delete();
                                    if (delete) {
                                        context.getContentResolver().delete(contentUri, null, null);
                                        videoList.remove(position);
                                        notifyItemRemoved(position);
                                        notifyItemRangeChanged(position, videoList.size());
                                        Toast.makeText(context, "Video Deleted", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(context, "can't deleted", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                            alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            alertDialog.show();
                            bottomSheetDialog.dismiss();
                        }
                    });

                    bsView.findViewById(R.id.bs_properties).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
                            alertDialog.setTitle("Properties");

                            String one = "File: " + videoList.get(position).getDisplayName();

                            String path = videoList.get(position).getPath();
                            int indexOfPath = path.lastIndexOf("/");
                            String two = "Path: " + path.substring(0, indexOfPath);

                            String three = "Size: " + android.text.format.Formatter
                                    .formatFileSize(context, Long.parseLong(videoList.get(position).getSize()));

                            String four = "Length: " + Utility.timeConversion((long) milliSeconds);

                            String namewithFormat = videoList.get(position).getDisplayName();
                            int index = namewithFormat.lastIndexOf(".");
                            String format = namewithFormat.substring(index + 1);
                            String five = "Format: " + format;

                            MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                            metadataRetriever.setDataSource(videoList.get(position).getPath());
                            String height = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                            String width = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                            String six = "Resolution: " + width + "x" + height;

                            alertDialog.setMessage(one + "\n\n" + two + "\n\n" + three + "\n\n" + four +
                                    "\n\n" + five + "\n\n" + six);
                            alertDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            alertDialog.show();
                            bottomSheetDialog.dismiss();
                        }
                    });

                    bsView.findViewById(R.id.bs_compress).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(context, CompressSingleActivity.class);
                            intent.setData( Uri.fromFile(new File(videoList.get(position).getPath())));
                            context.startActivity(intent);
                            bottomSheetDialog.dismiss();
                        }
                    });

                    bottomSheetDialog.setContentView(bsView);
                    bottomSheetDialog.show();

                }
            });
        } else {
            holder.menu_more.setVisibility(View.GONE);
            holder.videoName.setTextColor(Color.WHITE);
            holder.videoSize.setTextColor(Color.WHITE);
        }


        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, VideoPlayerActivity.class);
                intent.putExtra("position", position);
                intent.putExtra("video_title", videoList.get(position).getDisplayName());
                Bundle bundle = new Bundle();
                bundle.putParcelableArrayList("videoArrayList", videoList);
                intent.putExtras(bundle);
                context.startActivity(intent);
                if (viewType == 1) {
                    ((Activity) context).finish();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public void updateVideoFiles(ArrayList<MediaFiles> files) {
        videoList = new ArrayList<>();
        videoList.addAll(files);
        notifyDataSetChanged();
    }

    private void compressVideo(String inputPath) {

        File inputFile = new File(inputPath);

        try {
//            VideoCompressor compressor = new VideoCompressor(context, new VideoCompressor.CompressListener() {
//                @Override
//                public void onStart() {
//
//                }
//
//                @Override
//                public void onSuccess(Uri uri) {
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                        NotificationChannel channel1 = new NotificationChannel(
//                                "channel1",
//                                "Channel 1",
//                                NotificationManager.IMPORTANCE_HIGH
//                        );
//                        channel1.setDescription("This is Channel 1");
//
//
//                        NotificationManager manager = context.getSystemService(NotificationManager.class);
//                        manager.createNotificationChannel(channel1);
//                        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, "channel1")
//                                .setSmallIcon(R.drawable.ic_compress)
//                                .setContentTitle("Compress Success")
//                                .setContentText("Compress success, refresh the page")
//                                .setPriority(NotificationCompat.PRIORITY_LOW)
//                                .setOnlyAlertOnce(true);
//
//                        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
//                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//                            // TODO: Consider calling
//                            //    ActivityCompat#requestPermissions
//                            // here to request the missing permissions, and then overriding
//                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                            //                                          int[] grantResults)
//                            // to handle the case where the user grants the permission. See the documentation
//                            // for ActivityCompat#requestPermissions for more details.
//                            return;
//                        } else {
//
//                        }
//                        notificationManager.notify(2, notification.build());
//                    }
//                }
//
//                @Override
//                public void onFail() {
//
//                }
//
//                @Override
//                public void onProgress(float percent) {
//
//                }
//            });
//            compressor.setInput(new HandleVideo(Uri.fromFile(inputFile)));
//            compressor.setProfileH264High();
//            compressor.start();

        } catch (Throwable e) {
            Log.e(TAG, "Problem: " + e);
            e.printStackTrace();
        }
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
