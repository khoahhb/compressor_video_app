package com.example.compress_video_app.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.compress_video_app.R;
import com.example.compress_video_app.adapters.CompressedVideosAdapter;
import com.example.compress_video_app.compressor.VideoCompressor;
import com.example.compress_video_app.database.CompressedVideoRepository;
import com.example.compress_video_app.models.CompressedVideosDialog;
import com.example.compress_video_app.models.HandleVideo;
import com.example.compress_video_app.models.OnSwipeTouchListener;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class CompressSingleActivity extends AppCompatActivity {

    private static final String TAG = "CompressSingleActivity";

    private static final int REQUEST_PERMISSION_CODE = 1;
    private CompressedVideoRepository repository;
    private List<File> videoFiles;
    private ArrayList<HandleVideo> mediaFilesList;
    private CompressedVideosDialog playlistDialog;

    private AlertDialog compressDialog;
    private AlertDialog infoDialog;

    private Uri videoInputUri;
    private Uri videoOutputUri;

    private HandleVideo inputHandleVideo;
    private HandleVideo outputHandleVideo;


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
    private TextView tvInfo;


    private LinearLayout llPercent;
    private LinearLayout llQuality;
    private LinearLayout llSize;
    private LinearLayout llProfile;

    private AutoCompleteTextView tieCodec;
    private AutoCompleteTextView tieMethod;
    private AutoCompleteTextView tieProfile;
    private AutoCompleteTextView tieQuality;
    private TextInputEditText tieSize;

    private Button btnCompress, btnShowSetting;

    private Slider sldPercent;

    private LinearProgressIndicator pbIsCompressed;

    private boolean isCrossCheckedOrigin;
    private boolean isCrossCheckedCompress;
    private boolean singleTapOrigin = false;
    private boolean singleTapCompress = false;
    private long startTime = 0, endTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compress_single);
        getSupportActionBar().hide();

        repository = new CompressedVideoRepository(getApplication());

        initUI();

        setListeners();

        Uri uri = getIntent().getData();
        if (uri != null)
            getDataInput(uri);
        else
            return;

        int position = getIntent().getIntExtra("position", -1);

        if (position != -1)
            getDataOutput(Uri.fromFile(new File(mediaFilesList.get(position).getPath())));

        setDataToCompressDialog();
    }

    private void initUI() {

        LayoutInflater inflater = this.getLayoutInflater();
        View compressView = inflater.inflate(R.layout.compress_dialog, null);

        compressDialog = new MaterialAlertDialogBuilder(CompressSingleActivity.this,
                R.style.ThemeOverlay_Catalog_MaterialAlertDialog_Centered_FullWidthButtons)
                .setTitle("Video Quality & Size")
                .setView(compressView)
                .setPositiveButton("Apply Settings", (dialog, which) -> dialog.dismiss())
                .setNegativeButton("Go default", (dialog, which) -> {
                    dialog.dismiss();
                    setDataToCompressDialog();
                })
                .create();

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
        btnCompress = findViewById(R.id.btnCompress);
        btnShowSetting = findViewById(R.id.btnShowSetting);
        pbIsCompressed = findViewById(R.id.pbIsCompressed);

        tvInfo = compressView.findViewById(R.id.tvInfo);
        llPercent = compressView.findViewById(R.id.llPercent);
        llProfile = compressView.findViewById(R.id.llProfile);
        llQuality = compressView.findViewById(R.id.llQuality);
        llSize = compressView.findViewById(R.id.llSize);
        tieCodec = compressView.findViewById(R.id.tieCodec);
        tieMethod = compressView.findViewById(R.id.tieMethod);
        tieProfile = compressView.findViewById(R.id.tieProfile);
        tieQuality = compressView.findViewById(R.id.tieQuality);
        tieSize = compressView.findViewById(R.id.tieSize);
        sldPercent = compressView.findViewById(R.id.sldPercent);
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void setListeners() {

        videoListOrigin.setOnClickListener(v -> {
            if (videoInputUri != null && playlistDialog != null) {
                playlistDialog.show(getSupportFragmentManager(), playlistDialog.getTag());
            }
        });

        video_moreOrigin.setOnClickListener(v -> {
            if (videoInputUri != null) {
                showInfoDialog(inputHandleVideo);
            }
        });

        video_moreCompress.setOnClickListener(v -> {
            if (videoOutputUri != null) {
                showInfoDialog(outputHandleVideo);
            }
        });

        playerViewOrigin.setOnTouchListener(new OnSwipeTouchListener(this) {
            @SuppressLint("ClickableViewAccessibility")
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
            @SuppressLint("ClickableViewAccessibility")
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

        btnCompress.setOnClickListener(v -> compressVideo());

        btnShowSetting.setOnClickListener(v -> compressDialog.show());

        tieMethod.setOnItemClickListener((parent, view, position, id) -> {

            llPercent.setVisibility(View.GONE);
            sldPercent.setValue(60);
            llProfile.setVisibility(View.GONE);
            tieProfile.setText(getResources().getStringArray(R.array.simple_items)[0], false);
            llQuality.setVisibility(View.GONE);
            tieQuality.setText(getResources().getStringArray(R.array.quality_items)[0], false);
            llSize.setVisibility(View.GONE);
            if (videoInputUri != null) {
                float f = inputHandleVideo.getFloatSize(CompressSingleActivity.this);
                tieSize.setText(String.format("%.2f", f * 0.6) + "");
            } else {
                tieSize.setText("");
            }

            if (position == 0)
                llPercent.setVisibility(View.VISIBLE);
            else if (position == 1)
                llSize.setVisibility(View.VISIBLE);
            else if (position == 2)
                llProfile.setVisibility(View.VISIBLE);
            else if (position == 3)
                llQuality.setVisibility(View.VISIBLE);
        });
    }

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    private void setDataToCompressDialog() {
        if (videoInputUri != null) {
            tvInfo.setText(inputHandleVideo.getName() + " - " + inputHandleVideo.getFormatSize(CompressSingleActivity.this));
            float f = inputHandleVideo.getFloatSize(CompressSingleActivity.this);
            tieSize.setText(String.format("%.2f", f * 0.6) + "");
        } else {
            tvInfo.setText("");
            tieSize.setText("");
        }

        tieCodec.setText(getResources().getStringArray(R.array.codec_items)[0], false);
        tieMethod.setText(getResources().getStringArray(R.array.method_items)[0], false);
        tieProfile.setText(getResources().getStringArray(R.array.simple_items)[0], false);
        tieQuality.setText(getResources().getStringArray(R.array.quality_items)[0], false);
        sldPercent.setValue(60);

        llProfile.setVisibility(View.GONE);
        llQuality.setVisibility(View.GONE);
        llSize.setVisibility(View.GONE);
        llPercent.setVisibility(View.VISIBLE);
    }

    private void getDataInput(Uri uri) {

        videoInputUri = uri;

        inputHandleVideo = new HandleVideo(uri);

        videoListOrigin.setVisibility(View.VISIBLE);
        total_duration_Origin.setText(inputHandleVideo.getFormatDuration());
        video_titleOrigin.setText(inputHandleVideo.getName());

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
            public void onPlayerError(@NonNull ExoPlaybackException error) {
                Toast.makeText(CompressSingleActivity.this, "Video Playing Error", Toast.LENGTH_SHORT).show();
                Log.e("ProblemNeMa", error.getMessage());
            }
        });
        playerOrigin.setPlayWhenReady(true);

        videoFiles = getVideoFiles(new File(getFilesDir() + File.separator + "temp_videos"));
        mediaFilesList = new ArrayList<>();

        for (File file : videoFiles) {
            HandleVideo video_temp;
            try {
                video_temp = new HandleVideo(Uri.fromFile(file));
                if (video_temp.getDuration() == 0)
                    file.delete();
                else {
                    if (video_temp.getName().contains(inputHandleVideo.getName()))
                        mediaFilesList.add(video_temp);
                }
            } catch (Exception e) {
                file.delete();
            }
        }
        CompressedVideosAdapter videoFilesAdapter = null;

        playlistDialog = new CompressedVideosDialog(mediaFilesList, videoFilesAdapter, uri1 -> {
            HandleVideo video = repository.getVideoByName(new HandleVideo(uri1).getName());

            tvPercent.setVisibility(View.GONE);
            tvTime.setVisibility(View.VISIBLE);
            tvTotal.setVisibility(View.VISIBLE);
            tvTime.setText(video.getCompressStartEnd());
            tvTotal.setText(video.getCompressTotal());

            getDataOutput(uri1);
        });
    }

    @SuppressLint("SetTextI18n")
    private void getDataOutput(Uri uri) {

        videoOutputUri = uri;
        File realFile = new File(videoOutputUri.getPath());
        outputHandleVideo = new HandleVideo(Uri.fromFile(realFile));

        tvPercent.setText(0 + "%");
        pbIsCompressed.setProgress(0);
        total_duration_Compress.setText(outputHandleVideo.getFormatDuration());
        video_titleCompress.setText(outputHandleVideo.getName());

        tvPercent.setVisibility(View.GONE);
        pbIsCompressed.setVisibility(View.GONE);
        playerViewCompress.setVisibility(View.VISIBLE);
        videoListCompress.setVisibility(View.GONE);

        MediaItem mediaItem = MediaItem.fromUri(videoOutputUri);
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(
                new DefaultDataSourceFactory(
                        this,
                        Util.getUserAgent(this, "app")))
                .createMediaSource(mediaItem);

        playerCompress = new SimpleExoPlayer.Builder(this).build();
        playerCompress.setMediaSource(mediaSource);
        playerCompress.prepare();

        playerViewCompress.setPlayer(playerCompress);
        playerViewCompress.setKeepScreenOn(true);

        playerCompress.addListener(new Player.EventListener() {
            @Override
            public void onPlayerError(@NonNull ExoPlaybackException error) {
                Log.e("ProblemNeMa", error.getMessage());
            }
        });
        playerCompress.setPlayWhenReady(true);
    }

    @SuppressLint("SetTextI18n")
    private void compressVideo() {

        startTime = System.currentTimeMillis();
        tvTime.setText("Start - End: " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
        tvTotal.setText("Total (second): " + 0);

        pbIsCompressed.setVisibility(View.VISIBLE);
        tvPercent.setVisibility(View.VISIBLE);
        tvTime.setVisibility(View.VISIBLE);
        tvTotal.setVisibility(View.VISIBLE);
        playerViewCompress.setVisibility(View.GONE);

        try {
            VideoCompressor compressor = new VideoCompressor(this, new VideoCompressor.CompressListener() {

                @Override
                public void onSuccess(Uri uri) {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    @SuppressLint("SetTextI18n") Runnable myRunnable = () -> {
                        MediaScannerConnection.scanFile(CompressSingleActivity.this, new String[]{uri.getPath()}, null, null);

                        File realFile = new File(uri.getPath());

                        HandleVideo video = new HandleVideo(Uri.fromFile(realFile));

                        endTime = System.currentTimeMillis();
                        tvTime.setText(tvTime.getText() + " - " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
                        tvTotal.setText("Total (second): " + (endTime - startTime) / 1000);

                        video.setParentPath(videoInputUri.getPath());
                        video.setCompressStartEnd(tvTime.getText().toString());
                        video.setCompressTotal(tvTotal.getText().toString());

                        int position = findExist(video);

                        if (position != -1) {
                            videoFiles.set(position, realFile);
                            mediaFilesList.set(position, video);
                        } else {
                            videoFiles.add(realFile);
                            mediaFilesList.add(video);
                        }

                        playlistDialog.updateList(mediaFilesList);
                        repository.insertVideo(video);
                        getDataOutput(uri);
                        showInfoDialog(video);
                    };
                    mainHandler.post(myRunnable);
                }

                @Override
                public void onFail() {

                }

                @Override
                public void onProgress(int percent) {
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    Runnable myRunnable = () -> {
                        pbIsCompressed.setProgress(percent);
                        tvPercent.setText(percent + "%");
                    };
                    mainHandler.post(myRunnable);
                }
            });

            compressor.setInput(inputHandleVideo);
            compressor.isSaveInternal(true);
            compressor.isAudioCompress(false);

            if (tieCodec.getText().toString().equals("H264"))
                compressor.setCodecH264();
            else
                compressor.setCodecH265();

            String method = tieMethod.getText().toString();
            String[] methods = getResources().getStringArray(R.array.method_items);

            if (method.equals(methods[0])) {
                compressor.setPercent((int) sldPercent.getValue());
            } else if (method.equals(methods[1])) {

                float chosenSize = Float.parseFloat(tieSize.getText().toString());

                float min = (float) (inputHandleVideo.getFloatSize(CompressSingleActivity.this) * 0.1);
                float max = (float) (inputHandleVideo.getFloatSize(CompressSingleActivity.this) * 0.7);

                if (chosenSize < min || chosenSize > max)
                    Toast.makeText(CompressSingleActivity.this, "Size must be 10% greater and less than 70% of original size", Toast.LENGTH_LONG).show();
                else {
                    compressor.setTargetSize(chosenSize, CompressSingleActivity.this);
                }
            } else if (method.equals(methods[2])) {
                String codec = tieCodec.getText().toString().trim();

                if (codec.equals("High Quality-Size"))
                    compressor.setProfileNormal();
                else if (codec.equals("Medium Quality-Size")) {
                    compressor.setProfileMedium();
                } else {
                    compressor.setProfileHigh();
                }
            } else if (method.equals(methods[3])) {
                compressor.setProfileNormal();
            }
            compressor.start();

        } catch (Throwable e) {
            Log.e(TAG, "Problem: " + e);
            e.printStackTrace();
        }
    }

    private void showInfoDialog(HandleVideo video) {

        String one = "File: " + video.getName();

        String two = "Size: " + video.getFormatSize(CompressSingleActivity.this);

        String three = "Resolution: " + video.getFormatResolution();

        String four = "Duration: " + video.getFormatDuration();

        String five = "Format: " + video.getMime();

        String six = "Codec: " + video.getCodec();
        String seven = "Bitrate: " + video.getFormatBitrate();
        String eight = "Frame rate: " + video.getFormatFrameRate();

        new MaterialAlertDialogBuilder(CompressSingleActivity.this,
                R.style.ThemeOverlay_Catalog_MaterialAlertDialog_Centered_FullWidthButtons)
                .setTitle("Properties")
                .setMessage(one + "\n\n" + two + "\n\n" + three + "\n\n" + four +
                        "\n\n" + five + "\n\n" + six + "\n\n" + seven + "\n\n" + eight)
                .setPositiveButton("OK", null)
                .show();
    }

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
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
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


}