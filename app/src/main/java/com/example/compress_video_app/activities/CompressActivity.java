package com.example.compress_video_app.activities;

import android.Manifest;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.compress_video_app.R;
import com.example.compress_video_app.compressor.HandleVideo;
import com.example.compress_video_app.compressor.VideoCompressor;
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
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.Objects;

public class CompressActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_CODE = 1;
    private static final String TAG = "CompressActivity";

    private MediaFiles mOriginVideo;

    //UI
    private PlayerView playerViewOrigin;
    private PlayerView playerViewCompress;
    private SimpleExoPlayer playerOrigin;
    private SimpleExoPlayer playerCompress;

    private ImageView scalingOrigin, videoListOrigin,exo_rotateOrigin,video_moreOrigin;
    private ImageView scalingCompress, videoListCompress,exo_rotateCompress,video_moreCompress;

    private TextView video_titleOrigin;
    private TextView video_titleCompress;

    private ConcatenatingMediaSource concatenatingMediaSourceOrigin;
    private ConcatenatingMediaSource concatenatingMediaSourceCompress;

    private boolean isCrossCheckedOrigin;
    private boolean isCrossCheckedCompress;

    private TextView total_duration_Origin;
    private TextView total_duration_Compress;

    private boolean singleTapOrigin = false;
    private boolean singleTapCompress = false;


    private TextInputLayout tilCodec;
    private AutoCompleteTextView tieCodec;
    private Button btnUpVideo;
    private Button btnCompress;
    private ProgressBar pbIsCompressed;
    private Uri videoInputUri;
    private Uri videoOutputUri;
    private final ActivityResultLauncher<Intent> selectVideo = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (result.getData() != null) {
                        try {
                            getDataInput(result.getData().getData());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
    );


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_CODE:
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    uploadVideo();
                }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compress);
        getSupportActionBar().hide();
        initUI();
        setListeners();
    }

    private void initUI() {
        playerViewOrigin = findViewById(R.id.playerViewOrigin);
        playerViewCompress = findViewById(R.id.playerViewCompress);
        total_duration_Origin = findViewById(R.id.exo_durationOrigin);
        total_duration_Compress = findViewById(R.id.exo_durationCompress);
        scalingOrigin = findViewById(R.id.scalingOrigin);
        scalingCompress = findViewById(R.id.scalingCompress);
        exo_rotateOrigin = findViewById(R.id.exo_rotateOrigin);
        exo_rotateCompress = findViewById(R.id.exo_rotateCompress);
        videoListOrigin = findViewById(R.id.video_listOrigin);
        videoListCompress = findViewById(R.id.video_listCompress);
        video_moreOrigin = findViewById(R.id.video_moreOrigin);
        video_moreCompress = findViewById(R.id.video_moreCompress);
        video_titleOrigin = findViewById(R.id.video_titleOrigin);
        video_titleCompress = findViewById(R.id.video_titleCompress);

        tilCodec = findViewById(R.id.tilCodec);
        tieCodec = findViewById(R.id.tieCodec);
        btnUpVideo = findViewById(R.id.btnUpVideo);
        btnCompress = findViewById(R.id.btnCompress);
        pbIsCompressed = findViewById(R.id.pbIsCompressed);
    }

    private void setListeners() {

        videoListOrigin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                PlaylistDialog playlistDialog = new PlaylistDialog(mVideoFiles, videoFilesAdapter);
//                playlistDialog.show(getSupportFragmentManager(), playlistDialog.getTag());
            }
        });

        videoListCompress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                PlaylistDialog playlistDialog = new PlaylistDialog(mVideoFiles, videoFilesAdapter);
//                playlistDialog.show(getSupportFragmentManager(), playlistDialog.getTag());
            }
        });

        scalingOrigin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewGroup.LayoutParams layoutParams = playerViewOrigin.getLayoutParams();
                if (layoutParams.height ==ViewGroup.LayoutParams.MATCH_PARENT  ){
                    int desiredHeightDp = 240;
                    int desiredHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, desiredHeightDp, getResources().getDisplayMetrics());
                    layoutParams.height = desiredHeightPx;
                    playerViewOrigin.setLayoutParams(layoutParams);
                }else {
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    playerViewOrigin.setLayoutParams(layoutParams);
                }
            }
        });

        scalingCompress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewGroup.LayoutParams layoutParams = playerViewCompress.getLayoutParams();
                if (layoutParams.height ==ViewGroup.LayoutParams.MATCH_PARENT  ){
                    int desiredHeightDp = 240;
                    int desiredHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, desiredHeightDp, getResources().getDisplayMetrics());
                    layoutParams.height = desiredHeightPx;
                    playerViewCompress.setLayoutParams(layoutParams);
                }else {
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    playerViewCompress.setLayoutParams(layoutParams);
                }
            }
        });

        exo_rotateOrigin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            }
        });

        exo_rotateCompress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            }
        });

        video_moreOrigin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(videoInputUri!= null){

                    String realPath = getRealPathFromURI(CompressActivity.this, videoInputUri);

                    File realFile = new File(realPath);

                    HandleVideo video = new HandleVideo(Uri.fromFile(realFile));

                    android.app.AlertDialog.Builder alertDialog = new android.app.AlertDialog.Builder(CompressActivity.this);
                    alertDialog.setTitle("Properties");

                    String one = "File: " + video.getFormatFileName() ;

                    String two = "Size: " + video.getFormatSize(CompressActivity.this);

                    String three = "Resolution: " + video.getFormatResolution() ;

                    String four = "Duration: " + video.getFormatDuration();

                    String five = "Format: " + video.getFormat();

                    String six = "Codec: " + video.getFormatCodec() ;
                    String seven = "Bitrate: " + video.getFormatBitrate();
                    String eight = "Frame rate: " + video.getFormatFrameRate() ;

                    alertDialog.setMessage(one + "\n\n" + two + "\n\n" + three + "\n\n" + four +
                            "\n\n" + five + "\n\n" + six+ "\n\n" + seven+ "\n\n" + eight);
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
                if(videoOutputUri!= null){

                    String realPath = videoOutputUri.getPath();

                    File realFile = new File(realPath);

                    HandleVideo video = new HandleVideo(Uri.fromFile(realFile));

                    android.app.AlertDialog.Builder alertDialog = new android.app.AlertDialog.Builder(CompressActivity.this);
                    alertDialog.setTitle("Properties");

                    String one = "File: " + video.getFormatFileName() ;

                    String two = "Size: " + video.getFormatSize(CompressActivity.this);

                    String three = "Resolution: " + video.getFormatResolution() ;

                    String four = "Duration: " + video.getFormatDuration();

                    String five = "Format: " + video.getFormat();

                    String six = "Codec: " + video.getFormatCodec() ;
                    String seven = "Bitrate: " + video.getFormatBitrate();
                    String eight = "Frame rate: " + video.getFormatFrameRate() ;

                    alertDialog.setMessage(one + "\n\n" + two + "\n\n" + three + "\n\n" + four +
                            "\n\n" + five + "\n\n" + six+ "\n\n" + seven+ "\n\n" + eight);
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
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        playerViewOrigin.showController();
                        break;

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
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        playerViewCompress.showController();
                        break;

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





        btnUpVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.MANAGE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_CODE);
                    } else {
                        uploadVideo();
                    }
                }
            }
        });

        btnCompress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                compressVideo();
            }
        });

        tieCodec.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (!Objects.requireNonNull(tieCodec
                        .getText()).toString().trim().isEmpty()) {
                    tilCodec.setError(null);

                }
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
        if(playerCompress != null && playerOrigin != null){
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
        if(playerCompress != null){
            playerCompress.setPlayWhenReady(true);
            playerCompress.getPlaybackState();
        }
        if(playerOrigin != null){
            playerOrigin.setPlayWhenReady(true);
            playerOrigin.getPlaybackState();
        }


    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if(playerOrigin != null && playerCompress != null){
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

    public void uploadVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        selectVideo.launch(intent);
    }

    private void getDataInput(Uri uri) {

        videoInputUri = uri;

        String realPath = getRealPathFromURI(this, uri);

        File realFile = new File(realPath);

        HandleVideo video = new HandleVideo(Uri.fromFile(realFile));

        videoListOrigin.setVisibility(View.GONE);
        total_duration_Origin.setText(video.getFormatDuration());
        video_titleOrigin.setText(video.getFormatFileName());

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
                Toast.makeText(CompressActivity.this, "Video Playing Error", Toast.LENGTH_SHORT).show();
                Log.e("ProblemNeMa", error.getMessage());
            }
        });
        playerOrigin.setPlayWhenReady(true);
    }

    private void getDataOutput(Uri uri) {

        videoOutputUri = uri;


        String realPath = uri.getPath();

        File realFile = new File(realPath);

        HandleVideo video = new HandleVideo(Uri.fromFile(realFile));

        videoListCompress.setVisibility(View.GONE);
        total_duration_Compress.setText(video.getFormatDuration());
        video_titleCompress.setText(video.getFormatFileName());

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
                Toast.makeText(CompressActivity.this, "Video Playing Error", Toast.LENGTH_SHORT).show();
                Log.e("ProblemNeMa", error.getMessage());
            }
        });
        playerCompress.setPlayWhenReady(true);
    }
    private void compressVideo() {

        if(videoInputUri == null){
            tilCodec.setError("Select input video");
        }else{
            String inputPath;
            inputPath = getRealPathFromURI(CompressActivity.this, videoInputUri);

            String codec =  tieCodec.getText().toString().trim();

            if(codec.isEmpty()){
                tilCodec.setError("Choose profile");
            }else {
                pbIsCompressed.setVisibility(View.VISIBLE);
                File inputFile = new File(inputPath);

                try {
                    VideoCompressor compressor = new VideoCompressor(this, new VideoCompressor.CompressListener() {
                        @Override
                        public void onStart() {

                        }

                        @Override
                        public void onSuccess(Uri uri) {
                            Handler mainHandler = new Handler(Looper.getMainLooper());
                            Runnable myRunnable = () -> {
                                pbIsCompressed.setVisibility(View.GONE);
                                playerViewCompress.setVisibility(View.VISIBLE);
                                getDataOutput(uri);
                            };
                            mainHandler.post(myRunnable);
                        }

                        @Override
                        public void onFail() {

                        }

                        @Override
                        public void onProgress(float percent) {

                        }
                    });
                    compressor.setInput(new HandleVideo(Uri.fromFile(inputFile)));

                    if(codec.equals("H264-Normal"))
                        compressor.setProfileH264Normal();
                    else if (codec.equals("H264-Medium"))
                        compressor.setProfileH264Medium();
                    else if (codec.equals("H264-High"))
                        compressor.setProfileH264High();
                    else if (codec.equals("H265-Normal"))
                        compressor.setProfileH265Normal();
                    else if (codec.equals("H265-Medium"))
                        compressor.setProfileH265Medium();
                    else if (codec.equals("H265-High"))
                        compressor.setProfileH265High();
                    compressor.start();

                } catch (Throwable e) {
                    Log.e(TAG, "Problem: " + e);
                    e.printStackTrace();
                }
            }
        }
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
}