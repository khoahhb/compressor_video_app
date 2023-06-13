package com.example.compress_video_app.compressor;

import android.net.Uri;

public class InputVideo {

    private Uri mUri;
    private long mStartTime = -1;
    private long mEndTime = -1;
    private int mVideoDuration;

    public InputVideo(Uri uri) {
        mUri = uri;
        mVideoDuration = MediaHelper.GetDuration(uri);
    }

    public void setStartTime(long startTime) {
        mStartTime = startTime;
    }

    public void setEndTime(int endTime) {
        mEndTime = endTime;
    }

    public Uri getUri() {
        return mUri;
    }

    public long getStartTime() {
        return mStartTime;
    }

    public long getEndTime() {
        return mEndTime;
    }

    public int getVideoDuration() {
        return mVideoDuration;
    }

}
