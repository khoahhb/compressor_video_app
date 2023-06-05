package com.example.compress_video_app;

import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;


public class MediaHelper {

    public static final String MIME_TYPE_AVC = "video/avc";

    public static Bitmap GetThumbnailFromVideo(Uri uri, long timeMs) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(uri.toString());
        return retriever.getFrameAtTime(timeMs * 1000);
    }

    public static int GetDuration(Uri uri) {
        return GetMediaMetadataRetrieverPropertyInteger(uri, MediaMetadataRetriever.METADATA_KEY_DURATION, 0);
    }

    public static int GetWidth(Uri uri) {
        return GetMediaMetadataRetrieverPropertyInteger(uri, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, 0);
    }

    public static int GetHeight(Uri uri) {
        return GetMediaMetadataRetrieverPropertyInteger(uri, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, 0);
    }

    public static int GetBitRate(Uri uri) {
        return GetMediaMetadataRetrieverPropertyInteger(uri, MediaMetadataRetriever.METADATA_KEY_BITRATE, 0);
    }

    public static int GetRotation(Uri uri) {
        return GetMediaMetadataRetrieverPropertyInteger(uri, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION, 0);
    }

    public static int GetMediaMetadataRetrieverPropertyInteger(Uri uri, int key, int defaultValue) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(uri.toString());
        String value = retriever.extractMetadata(key);

        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    public static int GetIFrameInterval(Uri uri) {
        return GetMediaFormatPropertyInteger(uri, MediaFormat.KEY_I_FRAME_INTERVAL, -1);
    }

    public static int GetFrameRate(Uri uri) {
        return GetMediaFormatPropertyInteger(uri, MediaFormat.KEY_FRAME_RATE, -1);
    }

    public static int GetMediaFormatPropertyInteger(Uri uri, String key, int defaultValue) {

        int value = defaultValue;

        MediaExtractor extractor = new MediaExtractor();

        try {
            extractor.setDataSource(uri.toString());
        } catch (IOException e) {
            e.printStackTrace();
            return value;
        }

        MediaFormat format = GetTrackFormat(extractor, MIME_TYPE_AVC);
        extractor.release();

        if (format.containsKey(key)) {
            value = format.getInteger(key);
        }

        return value;

    }

    public static MediaFormat GetTrackFormat(MediaExtractor extractor, String mimeType) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String trackMimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.equals(trackMimeType)) {
                return format;
            }
        }
        return null;
    }

}
