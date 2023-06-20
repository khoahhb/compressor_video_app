package com.example.compress_video_app.models;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.example.compress_video_app.compressor.MediaHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.Serializable;

@Entity(tableName = "videos")
public class HandleVideo implements Serializable {


    @Ignore
    private Uri uri;

    @PrimaryKey
    @NonNull
    private String name;
    private String path;
    private String compressStartEnd;
    private String compressTotal;
    @Ignore
    private String mime;
    @Ignore
    private String codec;
    @Ignore
    private int duration;
    @Ignore
    private int bitrate;
    @Ignore
    private int frameRate;
    @Ignore
    private int width;
    @Ignore
    private int height;
    @Ignore
    private long size;
    @Ignore
    private long startTime = -1;
    @Ignore
    private long endTime = -1;

    public HandleVideo() {

    }

    public HandleVideo(Uri uri, String path, String name, String compressStartEnd, String compressTotal,
                       String mime, String codec, int duration, int bitrate, int frameRate, int width,
                       int height, long size, long startTime, long endTime) {
        this.uri = uri;
        this.path = path;
        this.name = name;
        this.compressStartEnd = compressStartEnd;
        this.compressTotal = compressTotal;
        this.mime = mime;
        this.codec = codec;
        this.duration = duration;
        this.bitrate = bitrate;
        this.frameRate = frameRate;
        this.width = width;
        this.height = height;
        this.size = size;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public HandleVideo(Uri tUri) {
        this.uri = tUri;
        this.path = uri.getPath();
        this.name = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
        this.compressStartEnd = "";
        this.compressTotal = "";
        this.mime = path.substring(path.lastIndexOf('.') + 1);
        this.codec = MediaHelper.GetCodec(uri);
        this.duration = MediaHelper.GetDuration(uri);
        this.bitrate = MediaHelper.GetBitRate(uri);
        this.frameRate = MediaHelper.GetFrameRate(uri);
        this.width = MediaHelper.GetWidth(uri);
        this.height = MediaHelper.GetHeight(uri);
        this.size = mGetSize();
        this.startTime = -1;
        this.endTime = -1;
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCompressStartEnd() {
        return compressStartEnd;
    }

    public void setCompressStartEnd(String compressStartEnd) {
        this.compressStartEnd = compressStartEnd;
    }

    public String getCompressTotal() {
        return compressTotal;
    }

    public void setCompressTotal(String compressTotal) {
        this.compressTotal = compressTotal;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public String getCodec() {
        return codec;
    }

    public void setCodec(String codec) {
        this.codec = codec;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getBitrate() {
        return bitrate;
    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }


    public long mGetSize() {
        File videoFile = new File(path);

        if (videoFile.exists()) {
            try {
                FileInputStream inputStream = new FileInputStream(videoFile);
                return inputStream.available();
            } catch (Exception e) {
                return 0;
            }

        } else {
            return 0;
        }
    }


    public String getFormatSize(Context context) {
        return android.text.format.Formatter
                .formatFileSize(context, size);
    }

    public String getFormatResolution() {
        return width + "x" + height;
    }

    public String getFormatDuration() {
        double milliseconds = Double.parseDouble(String.valueOf(duration));
        return Utility.timeConversion((long) milliseconds);
    }

    public String getFormatBitrate() {
        return bitrate / 1000 + " kBit/sec";
    }

    public String getFormatFrameRate() {
        return frameRate + " Frame/sec";
    }

    @Override
    public String toString() {
        return "HandleVideo{" +
                "uri=" + uri +
                ", path='" + path + '\'' +
                ", name='" + name + '\'' +
                ", compressStartEnd='" + compressStartEnd + '\'' +
                ", compressTotal='" + compressTotal + '\'' +
                ", mime='" + mime + '\'' +
                ", codec='" + codec + '\'' +
                ", duration=" + duration +
                ", bitrate=" + bitrate +
                ", frameRate=" + frameRate +
                ", width=" + width +
                ", height=" + height +
                ", size=" + size +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}
