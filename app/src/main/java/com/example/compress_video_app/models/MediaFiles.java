package com.example.compress_video_app.models;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.example.compress_video_app.compressor.MediaHelper;

import java.io.File;
import java.io.IOException;

public class MediaFiles implements Parcelable {
    public static final Creator<MediaFiles> CREATOR = new Creator<MediaFiles>() {
        @Override
        public MediaFiles createFromParcel(Parcel in) {
            return new MediaFiles(in);
        }

        @Override
        public MediaFiles[] newArray(int size) {
            return new MediaFiles[size];
        }
    };
    private String id;
    private String title;
    private String displayName;
    private String size;
    private String duration;
    private String path;
    private String dateAdded;
    private String bitrate;
    private String codec;
    private String frameRate;

    public MediaFiles(String id, String title, String displayName, String size, String duration, String path, String dateAdded, String bitrate) {
        this.id = id;
        this.title = title;
        this.displayName = displayName;
        this.size = size;
        this.duration = duration;
        this.path = path;
        this.dateAdded = dateAdded;
        this.bitrate = bitrate;
    }

    protected MediaFiles(Parcel in) {
        id = in.readString();
        title = in.readString();
        displayName = in.readString();
        size = in.readString();
        duration = in.readString();
        path = in.readString();
        dateAdded = in.readString();
        bitrate = in.readString();
    }

    private void updateCodecAndFrameRate() {
        codec = "";
        frameRate = "";

        MediaExtractor mediaExtractor = setupExtractorForVideo(path);

        if (mediaExtractor == null) {
            return;
        }

        int index = getVideoTrackIndex(mediaExtractor);

        mediaExtractor.selectTrack(index);

        MediaFormat videoFormat = mediaExtractor.getTrackFormat(index);

        Log.e("MetMoi", "MediaFiles VideoFormat" + videoFormat);

        if (videoFormat.containsKey(MediaFormat.KEY_MIME)) {
            codec = videoFormat.getString(MediaFormat.KEY_MIME);
        }
        if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            frameRate = "" + videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(String dateAdded) {
        this.dateAdded = dateAdded;
    }

    public String getBitrate() {
        int bitr = Integer.parseInt(bitrate);
        return "" + bitr / 1000;
    }

    public void setBitrate(String bitrate) {
        this.bitrate = bitrate;
    }

    public String getFormatSize(Context context) {
        return android.text.format.Formatter
                .formatFileSize(context, Long.parseLong(size));
    }

    public String getFormatDuration() {
        double milliseconds = Double.parseDouble(duration);
        return Utility.timeConversion((long) milliseconds);
    }

    public String getFormat() {
        int index = displayName.lastIndexOf(".");
        return displayName.substring(index + 1);
    }

    public String getFormatResolution() {
        File mOriginV = new File(path);

        return MediaHelper.GetWidth(Uri.fromFile(mOriginV)) +
                "x" + MediaHelper.GetHeight(Uri.fromFile(mOriginV));
    }

    public String getCodec() {
        updateCodecAndFrameRate();
        return codec;
    }

    public void setCodec(String codec) {
        this.codec = codec;
    }

    public String getFrameRate() {
        updateCodecAndFrameRate();
        return frameRate;
    }

    public void setFrameRate(String frameRate) {
        this.frameRate = frameRate;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(title);
        dest.writeString(displayName);
        dest.writeString(size);
        dest.writeString(duration);
        dest.writeString(path);
        dest.writeString(dateAdded);
        dest.writeString(bitrate);
    }

    @Override
    public String toString() {
        return "MediaFiles{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", displayName='" + displayName + '\'' +
                ", size='" + size + '\'' +
                ", duration='" + duration + '\'' +
                ", path='" + path + '\'' +
                ", dateAdded='" + dateAdded + '\'' +
                ", bitrate='" + bitrate + '\'' +
                '}';
    }


    private MediaExtractor setupExtractorForVideo(String inputPath) {

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(inputPath);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return extractor;
    }

    private int getVideoTrackIndex(MediaExtractor extractor) {
        int index = -1;
        for (int trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++) {
            MediaFormat format = extractor.getTrackFormat(trackIndex);

            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null) {
                if (mime.startsWith("video/")) {
                    index = trackIndex;
                }
            }
        }
        return index;
    }
}
