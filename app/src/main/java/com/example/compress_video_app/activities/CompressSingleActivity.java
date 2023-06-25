package com.example.compress_video_app.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.compress_video_app.R;
import com.example.compress_video_app.adapters.CompressedVideosAdapter;
import com.example.compress_video_app.compressor.VideoCompressor;
import com.example.compress_video_app.database.CompressedVideoRepository;
import com.example.compress_video_app.models.CompressedVideosDialog;
import com.example.compress_video_app.models.HandleVideo;
import com.example.compress_video_app.models.MediaFiles;
import com.example.compress_video_app.models.OnSwipeTouchListener;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;


public class CompressSingleActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_CODE = 1;
    private static final String TAG = "CompressSingleActivity";

    private MediaFiles mOriginVideo;
    private CompressedVideoRepository repository;
    private List<File> videoFiles;
    private ArrayList<HandleVideo> mediaFilesList;
    private CompressedVideosDialog playlistDialog;

    //UI
    private PlayerView playerViewOrigin;
    private PlayerView playerViewCompress;
    private SimpleExoPlayer playerOrigin;
    private SimpleExoPlayer playerCompress;

    private ImageView videoListOrigin, video_moreOrigin;
    private ImageView videoListCompress, video_moreCompress;

    private TextView video_titleOrigin;
    private TextView video_titleCompress;
    private TextView total_duration_Origin;
    private TextView total_duration_Compress;
    private TextView tvPercent;
    private TextView tvTime;
    private TextView tvTotal;
//
//    private TextInputLayout tilCodec;
//    private AutoCompleteTextView tieCodec;
    private Button btnCompress,btnShowSetting;
    private LinearProgressIndicator pbIsCompressed;

    private ConcatenatingMediaSource concatenatingMediaSourceOrigin;
    private ConcatenatingMediaSource concatenatingMediaSourceCompress;

    private boolean isCrossCheckedOrigin;
    private boolean isCrossCheckedCompress;
    private boolean singleTapOrigin = false;
    private boolean singleTapCompress = false;

    private long startTime = 0, endTime = 0;


    private Uri videoInputUri;

    private Uri videoOutputUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compress_single);
        getSupportActionBar().hide();
        repository = new CompressedVideoRepository(getApplication());
        initUI();
        setListeners();

        Uri uri = getIntent().getData();

        if (uri != null) {
            getDataInput(uri);
        }
    }

    private void initUI() {
        playerViewOrigin = findViewById(R.id.playerViewOrigin);
        playerViewCompress = findViewById(R.id.playerViewCompress);
        total_duration_Origin = findViewById(R.id.exo_durationOrigin);
        total_duration_Compress = findViewById(R.id.exo_durationCompress);
        videoListOrigin = findViewById(R.id.video_listOrigin);
        videoListCompress = findViewById(R.id.video_listCompress);
        video_moreOrigin = findViewById(R.id.video_moreOrigin);
        video_moreCompress = findViewById(R.id.video_moreCompress);
        video_titleOrigin = findViewById(R.id.video_titleOrigin);
        video_titleCompress = findViewById(R.id.video_titleCompress);

        tvPercent = findViewById(R.id.tvPercent);
        tvTime = findViewById(R.id.tvTime);
        tvTotal = findViewById(R.id.tvTotal);
//        tilCodec = findViewById(R.id.tilCodec);
//        tieCodec = findViewById(R.id.tieCodec);
        btnCompress = findViewById(R.id.btnCompress);
        btnShowSetting = findViewById(R.id.btnShowSetting);
        pbIsCompressed = findViewById(R.id.pbIsCompressed);
    }

    private void setListeners() {

        videoListOrigin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoInputUri != null) {
                    playlistDialog.show(getSupportFragmentManager(), playlistDialog.getTag());
                }
            }
        });

        video_moreOrigin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoInputUri != null) {

                    HandleVideo video = new HandleVideo(videoInputUri);

                    android.app.AlertDialog.Builder alertDialog = new android.app.AlertDialog.Builder(CompressSingleActivity.this);
                    alertDialog.setTitle("Properties");

                    String one = "File: " + video.getName();

                    String two = "Size: " + video.getFormatSize(CompressSingleActivity.this);

                    String three = "Resolution: " + video.getFormatResolution();

                    String four = "Duration: " + video.getFormatDuration();

                    String five = "Format: " + video.getMime();

                    String six = "Codec: " + video.getCodec();
                    String seven = "Bitrate: " + video.getFormatBitrate();
                    String eight = "Frame rate: " + video.getFormatFrameRate();

                    alertDialog.setMessage(one + "\n\n" + two + "\n\n" + three + "\n\n" + four +
                            "\n\n" + five + "\n\n" + six + "\n\n" + seven + "\n\n" + eight);
                    alertDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    alertDialog.show();
                }
            }
        });

        video_moreCompress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoOutputUri != null) {

                    String realPath = videoOutputUri.getPath();

                    File realFile = new File(realPath);

                    HandleVideo video = new HandleVideo(Uri.fromFile(realFile));

                    android.app.AlertDialog.Builder alertDialog = new android.app.AlertDialog.Builder(CompressSingleActivity.this);
                    alertDialog.setTitle("Properties");

                    String one = "File: " + video.getName();

                    String two = "Size: " + video.getFormatSize(CompressSingleActivity.this);

                    String three = "Resolution: " + video.getFormatResolution();

                    String four = "Duration: " + video.getFormatDuration();

                    String five = "Format: " + video.getMime();

                    String six = "Codec: " + video.getCodec();
                    String seven = "Bitrate: " + video.getFormatBitrate();
                    String eight = "Frame rate: " + video.getFormatFrameRate();

                    alertDialog.setMessage(one + "\n\n" + two + "\n\n" + three + "\n\n" + four +
                            "\n\n" + five + "\n\n" + six + "\n\n" + seven + "\n\n" + eight);
                    alertDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    alertDialog.show();
                }
            }
        });

        playerViewOrigin.setOnTouchListener(new OnSwipeTouchListener(this) {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    playerViewOrigin.showController();
                }
                return super.onTouch(view, motionEvent);
            }

            @Override
            public void onSingleTouch() {
                super.onSingleTouch();
                if (singleTapOrigin) {
                    playerViewOrigin.showController();
                    singleTapOrigin = false;
                } else {
                    playerViewOrigin.hideController();
                    singleTapOrigin = true;
                }
            }
        });

        playerViewCompress.setOnTouchListener(new OnSwipeTouchListener(this) {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    playerViewCompress.showController();
                }
                return super.onTouch(view, motionEvent);
            }

            @Override
            public void onSingleTouch() {
                super.onSingleTouch();
                if (singleTapCompress) {
                    playerViewCompress.showController();
                    singleTapCompress = false;
                } else {
                    playerViewCompress.hideController();
                    singleTapCompress = true;
                }
            }
        });


        btnCompress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

//                compressVideo();
            }
        });

//        tieCodec.addTextChangedListener(new TextWatcher() {
//            @Override
//            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//
//            }
//
//            @Override
//            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//
//            }
//
//            @Override
//            public void afterTextChanged(Editable editable) {
//                if (!Objects.requireNonNull(tieCodec
//                        .getText()).toString().trim().isEmpty()) {
//                    tilCodec.setError(null);
//
//                }
//            }
//        });

        btnShowSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialAlertDialogBuilder(CompressSingleActivity.this)
                        .setTitle("test")
                        .setMessage("test")
                        .setPositiveButton("test", null)
                        .setNegativeButton("test", null)
                        .show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (playerOrigin != null) {
            playerOrigin.release();
        }
        if (playerCompress != null) {
            playerCompress.release();
        }
        super.onBackPressed();

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playerCompress != null && playerOrigin != null) {
            playerOrigin.setPlayWhenReady(false);
            playerOrigin.getPlaybackState();
            playerCompress.setPlayWhenReady(false);
            playerCompress.getPlaybackState();
            if (isInPictureInPictureMode()) {
                playerOrigin.setPlayWhenReady(true);
                playerCompress.setPlayWhenReady(true);
            } else {
                playerOrigin.setPlayWhenReady(false);
                playerOrigin.getPlaybackState();
                playerCompress.setPlayWhenReady(false);
                playerCompress.getPlaybackState();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playerCompress != null) {
            playerCompress.setPlayWhenReady(true);
            playerCompress.getPlaybackState();
        }
        if (playerOrigin != null) {
            playerOrigin.setPlayWhenReady(true);
            playerOrigin.getPlaybackState();
        }


    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (playerOrigin != null && playerCompress != null) {
            playerOrigin.setPlayWhenReady(true);
            playerOrigin.getPlaybackState();
            playerCompress.setPlayWhenReady(true);
            playerCompress.getPlaybackState();
        }

    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        }
        isCrossCheckedCompress = isInPictureInPictureMode;
        isCrossCheckedOrigin = isInPictureInPictureMode;
        if (isInPictureInPictureMode) {
            playerViewOrigin.hideController();
            playerViewCompress.hideController();
        } else {
            playerViewCompress.showController();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isCrossCheckedOrigin) {
            playerOrigin.release();
            finish();
        }
        if (isCrossCheckedCompress) {
            playerCompress.release();
            finish();
        }
    }


    private void getDataInput(Uri uri) {

        videoInputUri = uri;

        HandleVideo video = new HandleVideo(uri);

        videoListOrigin.setVisibility(View.VISIBLE);
        total_duration_Origin.setText(video.getFormatDuration());
        video_titleOrigin.setText(video.getName());

        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(
                this, Util.getUserAgent(this, "app"));
        MediaItem mediaItem = MediaItem.fromUri(uri);
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem);

        playerOrigin = new SimpleExoPlayer.Builder(this).build();
        playerOrigin.setMediaSource(mediaSource);
        playerOrigin.prepare();

        playerViewOrigin.setPlayer(playerOrigin);
        playerViewOrigin.setKeepScreenOn(true);

        playerOrigin.addListener(new Player.EventListener() {
            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Toast.makeText(CompressSingleActivity.this, "Video Playing Error", Toast.LENGTH_SHORT).show();
                Log.e("ProblemNeMa", error.getMessage());
            }
        });
        playerOrigin.setPlayWhenReady(true);


        String folderName = "temp_videos";
        String folderPath = getFilesDir() + File.separator + folderName;

        videoFiles = getVideoFiles(new File(folderPath));
        mediaFilesList = new ArrayList<>();

        for (File file : videoFiles) {
            HandleVideo videot;
            try {
                videot = new HandleVideo(Uri.fromFile(file));
                if (video.getDuration() == 0)
                    file.delete();
                else {
                    if (videot.getName().contains(video.getName()))
                        mediaFilesList.add(videot);
                }
            } catch (Exception e) {
                file.delete();
            }
        }
        CompressedVideosAdapter videoFilesAdapter = null;

        playlistDialog = new CompressedVideosDialog(mediaFilesList, videoFilesAdapter, new CompressedVideosDialog.ElementListener() {
            @Override
            public void onClickHandle(Uri uri) {
                HandleVideo videoT = new HandleVideo(uri);
                tvPercent.setVisibility(View.GONE);
                tvTime.setVisibility(View.VISIBLE);
                tvTotal.setVisibility(View.VISIBLE);
                HandleVideo video = repository.getVideoByName(videoT.getName());
                tvTime.setText(video.getCompressStartEnd());
                tvTotal.setText(video.getCompressTotal());
                getDataOutput(uri);
            }
        });
    }

    private void getDataOutput(Uri uri) {

        tvPercent.setText(0 + "%");
        tvPercent.setVisibility(View.GONE);
        pbIsCompressed.setVisibility(View.GONE);
        pbIsCompressed.setProgress(0);
        playerViewCompress.setVisibility(View.VISIBLE);

        videoOutputUri = uri;

        String realPath = uri.getPath();

        File realFile = new File(realPath);

        HandleVideo video = new HandleVideo(Uri.fromFile(realFile));

        videoListCompress.setVisibility(View.GONE);
        total_duration_Compress.setText(video.getFormatDuration());
        video_titleCompress.setText(video.getName());

        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(
                this, Util.getUserAgent(this, "app"));
        MediaItem mediaItem = MediaItem.fromUri(uri);
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem);

        playerCompress = new SimpleExoPlayer.Builder(this).build();
        playerCompress.setMediaSource(mediaSource);
        playerCompress.prepare();

        playerViewCompress.setPlayer(playerCompress);
        playerViewCompress.setKeepScreenOn(true);

        playerCompress.addListener(new Player.EventListener() {
            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Log.e("ProblemNeMa", error.getMessage());
            }
        });
        playerCompress.setPlayWhenReady(true);
    }

//    private void compressVideo() {
//
//        if (videoInputUri == null) {
//            tilCodec.setError("Select input video");
//        } else {
//
//            String codec = tieCodec.getText().toString().trim();
//
//            if (codec.isEmpty()) {
//                tilCodec.setError("Choose profile");
//            } else {
//
//                startTime = System.currentTimeMillis();
//
//                pbIsCompressed.setVisibility(View.VISIBLE);
//                tvPercent.setVisibility(View.VISIBLE);
//                tvTime.setVisibility(View.VISIBLE);
//                tvTotal.setVisibility(View.VISIBLE);
//                playerViewCompress.setVisibility(View.GONE);
//
//                String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
//
//                tvTime.setText("Start - End: " + currentTime);
//                tvTotal.setText("Total (second): " + 0);
//
//                try {
//                    VideoCompressor compressor = new VideoCompressor(this, new VideoCompressor.CompressListener() {
//                        @Override
//                        public void onStart() {
//
//                        }
//
//                        @Override
//                        public void onSuccess(Uri uri) {
//                            Handler mainHandler = new Handler(Looper.getMainLooper());
//                            Runnable myRunnable = () -> {
//                                MediaScannerConnection.scanFile(CompressSingleActivity.this, new String[]{uri.getPath()}, null, null);
//
//                                endTime = System.currentTimeMillis();
//                                String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
//                                tvTime.setText(tvTime.getText() + " - " + currentTime);
//                                tvTotal.setText("Total (second): " + (endTime - startTime) / 1000);
//
//                                String realPath = uri.getPath();
//
//                                File realFile = new File(realPath);
//
//                                HandleVideo video = new HandleVideo(Uri.fromFile(realFile));
//
//                                video.setCompressStartEnd(tvTime.getText().toString());
//                                video.setCompressTotal(tvTotal.getText().toString());
//
//                                int position = findExist(video);
//
//                                if (position != -1) {
//                                    videoFiles.set(position, realFile);
//                                    mediaFilesList.set(position, video);
//                                } else {
//                                    videoFiles.add(realFile);
//                                    mediaFilesList.add(video);
//                                }
//
//                                playlistDialog.updateList(mediaFilesList);
//                                repository.insertVideo(video);
//                                getDataOutput(uri);
//                            };
//                            mainHandler.post(myRunnable);
//                        }
//
//                        @Override
//                        public void onFail() {
//
//                        }
//
//                        @Override
//                        public void onProgress(int percent) {
//                            Handler mainHandler = new Handler(Looper.getMainLooper());
//                            Runnable myRunnable = () -> {
//                                pbIsCompressed.setProgress(percent);
//                                tvPercent.setText(percent + "%");
//                            };
//                            mainHandler.post(myRunnable);
//                        }
//                    });
//                    compressor.setInput(new HandleVideo(videoInputUri));
//                    compressor.isSaveInternal(true);
//                    compressor.isAudioCompress(false);
//
//                    if (codec.equals("H264-Normal"))
//                        compressor.setProfileH264Normal();
//                    else if (codec.equals("H264-Medium"))
//                        compressor.setProfileH264Medium();
//                    else if (codec.equals("H264-High"))
//                        compressor.setProfileH264High();
//                    else if (codec.equals("H265-Normal"))
//                        compressor.setProfileH265Normal();
//                    else if (codec.equals("H265-Medium"))
//                        compressor.setProfileH265Medium();
//                    else if (codec.equals("H265-High"))
//                        compressor.setProfileH265High();
//
//                    compressor.start();
//
//                } catch (Throwable e) {
//                    Log.e(TAG, "Problem: " + e);
//                    e.printStackTrace();
//                }
//            }
//        }
//    }

    public String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Video.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private List<File> getVideoFiles(File folder) {
        List<File> videoFiles = new ArrayList<>();
        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    videoFiles.addAll(getVideoFiles(file));
                } else if (isVideoFile(file)) {
                    videoFiles.add(file);
                }
            }
        }

        return videoFiles;
    }

    private boolean isVideoFile(File file) {
        String name = file.getName();
        String extension = name.substring(name.lastIndexOf(".") + 1).toLowerCase();
        return extension.equals("mp4") || extension.equals("mkv") || extension.equals("avi");
    }

    private int findExist(HandleVideo mVideo) {
        for (int i = 0; i < mediaFilesList.size(); i++) {
            if (mediaFilesList.get(i).getName().equals(mVideo.getName()))
                return i;
        }
        return -1;
    }
}