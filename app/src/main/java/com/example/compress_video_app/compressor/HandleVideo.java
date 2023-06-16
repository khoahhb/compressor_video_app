package com.example.compress_video_app.compressor;

import android.content.Context;
import android.net.Uri;

import com.example.compress_video_app.models.Utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class HandleVideo {

    private final Uri mUri;
    private long mStartTime = -1;
    private long mEndTime = -1;
    private final int mVideoDuration;

    public HandleVideo(Uri uri) {
        mUri = uri;
        mVideoDuration = MediaHelper.GetDuration(uri);
    }

    public Uri getUri() {
        return mUri;
    }

    public long getStartTime() {
        return mStartTime;
    }

    public void setStartTime(long startTime) {
        mStartTime = startTime;
    }

    public long getEndTime() {
        return mEndTime;
    }

    public void setEndTime(int endTime) {
        mEndTime = endTime;
    }

    public int getVideoDuration() {
        return mVideoDuration;
    }

    public String getFormatFileName() {
        String inputPath = mUri.getPath();
        return inputPath.substring(inputPath.lastIndexOf('/') + 1, inputPath.lastIndexOf('.'));
    }
    public String getFormatSize(Context context) {
        File videoFile = new File(mUri.getPath());

        if(videoFile.exists()){
            try{
                FileInputStream inputStream = new FileInputStream(videoFile);

                long fileSizeInBytes = inputStream.available();
                return android.text.format.Formatter
                        .formatFileSize(context,fileSizeInBytes);
            }catch (Exception e){
                return "";
            }

        }else{
            return "";
        }
    }
    public String getFormatResolution(){
        File mOriginV = new File(mUri.getPath());

        return MediaHelper.GetWidth(Uri.fromFile(mOriginV)) +
                "x" + MediaHelper.GetHeight(Uri.fromFile(mOriginV));
    }
    public String getFormatDuration() {
        double milliseconds = Double.parseDouble(String.valueOf(mVideoDuration));
        return Utility.timeConversion((long) milliseconds);
    }
    public String getFormat() {
        String inputPath = mUri.getPath();
        return inputPath.substring(inputPath.lastIndexOf('.') + 1,inputPath.length());
    }
    public String getFormatCodec(){
        return MediaHelper.GetCodec(mUri);
    }
    public String getFormatBitrate(){
        int bitr =  MediaHelper.GetBitRate(mUri);
        return bitr / 1000 + " kBit/sec";
    }

    public String getFormatFrameRate(){
        return MediaHelper.GetFrameRate(mUri) + " Frame/sec";
    }

}
