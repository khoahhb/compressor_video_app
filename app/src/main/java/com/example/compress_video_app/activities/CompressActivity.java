package com.example.compress_video_app.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.compress_video_app.R;
import com.example.compress_video_app.compressor.InputVideo;
import com.example.compress_video_app.compressor.VideoCompressor;

import java.io.File;
import java.io.IOException;

public class CompressActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_CODE = 1;
    private static final String TAG =  "CompressActivity";
    private Button btnUpVideo,btnCompress,btnViewCompressedList;
    private VideoView vvOriginal;
    private VideoCompressor videoCompressor;
    private ProgressBar pbIsCompressed;
    private Uri videoUri;
    private final ActivityResultLauncher<Intent> selectVideo = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (result.getData() != null) {
                        videoUri = result.getData().getData();
                        try {
                            vvOriginal.setVideoURI(videoUri);
                            vvOriginal.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();

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
                try {
                    pbIsCompressed.setVisibility(View.VISIBLE);
                    compressVideo(getRealPathFromURI (CompressActivity.this, videoUri));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

    }

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

    public void initUI() {
        btnUpVideo = findViewById(R.id.btnUpVideo);
        btnCompress = findViewById(R.id.btnCompress);
        btnViewCompressedList = findViewById(R.id.btnViewCompressedList);
        pbIsCompressed = findViewById(R.id.pbIsCompressed);
        vvOriginal = findViewById(R.id.vvOriginal);
        MediaController mediaController = new MediaController(this);
        vvOriginal.setMediaController(mediaController);
        mediaController.setAnchorView(vvOriginal);
    }

    public void uploadVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        selectVideo.launch(intent);
    }

    private void compressVideo(String inputPath) throws IOException {

        File inputFile = new File(inputPath);

        try {
            VideoCompressor compressor = new VideoCompressor(this, new VideoCompressor.CompressListener() {
                @Override
                public void onStart() {

                }

                @Override
                public void onSuccess(String compressVideoPath) {

                }

                @Override
                public void onFail() {

                }

                @Override
                public void onProgress(float percent) {

                }
            });
            compressor.setInput(new InputVideo(Uri.fromFile(inputFile)));
            compressor.setProfileH264High();
            compressor.start();

        } catch (Throwable e) {
            Log.e(TAG, "Problem: " + e);
            e.printStackTrace();
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