package com.example.compress_video_app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.security.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_CODE = 1;
    private static final String TAG = "MainActivity";
    private Button btnUpVideo;
    private VideoView vvOriginal, vvCompressed;
    private VideoCompressor videoCompressor;
    private Uri videoUri;
    private File dir;

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

    }

    public void initUI() {

        dir = new File(this.getFilesDir(), "temp_videos");
        if (!dir.exists()) {
            dir.mkdir();
        }

        btnUpVideo = findViewById(R.id.btnUpVideo);
        vvOriginal = findViewById(R.id.vvOriginal);
        vvCompressed = findViewById(R.id.vvCompressed);
        MediaController mediaController = new MediaController(this);
        vvOriginal.setMediaController(mediaController);
        mediaController.setAnchorView(vvOriginal);
        MediaController mediaController2 = new MediaController(this);
        vvCompressed.setMediaController(mediaController2);
        mediaController2.setAnchorView(vvCompressed);
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

    public void uploadVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        selectVideo.launch(intent);
    }

    private final ActivityResultLauncher<Intent> selectVideo = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (result.getData() != null) {
                        videoUri = result.getData().getData();
                        try {
                            vvOriginal.setVideoURI(videoUri);
                            vvOriginal.start();
                            compressVideo(getRealPathFromURI(this, videoUri));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
    );

    private void compressVideo(String inputPath) throws IOException {

        File inputFile = new File(inputPath);
        File outputFile = new File(dir, "output.mp4");
        if (!outputFile.exists()) {
            outputFile.createNewFile();
        }

        try {
            VideoCompressor compressor = new VideoCompressor(new VideoCompressor.VideoCompressorListener() {
                @Override
                public void onSuccess() {
                    vvCompressed.setVideoURI(Uri.fromFile(outputFile));
                    vvCompressed.start();
                }
            });
            compressor.setInput(new InputVideo(Uri.fromFile(inputFile)));
            compressor.setOutput(Uri.fromFile(outputFile));
            compressor.setOutputResolution(1280, 720);
            compressor.start();
        } catch (Throwable e) {
            Log.e(TAG, "Problem: " + e);
            e.printStackTrace();
        }


//        try {
//            vvCompressed.setVideoURI(Uri.fromFile(outputFile));
//            vvCompressed.start();
//
//        } catch (Exception e) {
//            Log.e("VideoView", "Error occurred while setting video URI: " + e.getMessage());
//            e.printStackTrace();
//        }


//        File inputFile = new File(inputPath);
//        File outputFile = new File(dir, createOutputName(inputPath));
//        outputFile.createNewFile();
//        try {
//            VideoCompressor compressor = new VideoCompressor();
//            compressor.setInput(new InputVideo(Uri.fromFile(inputFile)));
//            compressor.setOutput(Uri.fromFile(outputFile));
//            compressor.setOutputResolution(1280, 720);
//            compressor.start();
//        } catch (Throwable e) {
//            Log.e(TAG, "Problem: " + e);
//            e.printStackTrace();
//        }
    }

    public String createOutputName(String inputPath) {
        String inputName = inputPath.substring(inputPath.lastIndexOf('/') + 1, inputPath.lastIndexOf('.'));

        String outputName = inputName + "_" + (new Date()).getTime() + ".mp4";

        return outputName;
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