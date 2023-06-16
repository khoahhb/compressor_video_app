package com.example.compress_video_app.compressor;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;


public class VideoCompressor {

    public static final int WIDTH_720P = 1280;
    public static final int HEIGHT_720P = 720;
    public static final int BITRATE_720P = 6000000;
    public static final int FPS_30 = 30; // 30fps
    public static final int FPS_15 = 15; // 15fps
    public static final int IFRAME_INTERVAL_10 = 10;
    private static final String TAG = "VideoCompressor";
    private static final int TIMEOUT_USE = 0;
    private static final boolean WORK_AROUND_BUGS = false; // avoid fatal codec bugs
    private static final boolean VERBOSE = false; // lots of logging
    private final File dir;
    private final NotificationManagerCompat notificationManager;
    private final Context mContext;
    private final File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    private final CompressListener mCompressListener;
    HandleVideo mInput = null;
    private String mMime = MediaHelper.MIME_TYPE_AVC;
    private int mWidth = WIDTH_720P;
    private int mHeight = HEIGHT_720P;
    private int mRotation = 0;
    private int mBitRate = BITRATE_720P;
    private int mFrameRate = FPS_30;
    private int mIFrameInterval = IFRAME_INTERVAL_10;
    private Uri mOutputTempUri;
    private Uri mOutputRealUri;
    private InputSurface mInputSurface;
    private OutputSurface mOutputSurface;
    private MediaMuxer mMuxer = null;
    private MediaCodec mVideoEncoder = null;
    private MediaCodec mAudioEncoder = null;
    private String mInputName;
    private String realName;
    private int mTrackVideoIndex = -1;
    private int mTrackAudioIndex = -1;
    private long mLastVideoSampleTime = 0;
    private long mLastAudioSampleTime = 0;
    private long mEncoderVideoPresentationTimeUs = 0;
    private final long mEncoderAudioPresentationTimeUs = 0;
    private MediaFormat mInputAudioFormat;
    private MediaFormat mInputVideoFormat;
    private MediaExtractor extractorVideo;
    private MediaExtractor extractorAudio;
    private MediaCodec decoderVideo;
    private MediaCodec decoderAudio;

    public VideoCompressor(Context context, CompressListener compressListener) {
        this.mCompressListener = compressListener;
        this.mContext = context;
        notificationManager = NotificationManagerCompat.from(this.mContext);
        dir = new File(mContext.getFilesDir(), "temp_videos");
        if (!dir.exists()) {
            dir.mkdir();
        }
    }

    public void setOutputBitRate(int bitRate) {
        mBitRate = bitRate;
    }

    public void setOutputFrameRate(int frameRate) {
        mFrameRate = frameRate;
    }

    public void setOutputIFrameInterval(int IFrameInterval) {
        mIFrameInterval = IFrameInterval;
    }

    public void setProfileH264Normal() throws IOException {
        mMime = MediaHelper.MIME_TYPE_AVC;
        mBitRate = mHeight * mWidth * 2;
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("normal-264-");
    }

    public void setProfileH264Medium() throws IOException {
        mMime = MediaHelper.MIME_TYPE_AVC;
        mBitRate = mHeight * mWidth;
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("medium-264-");
    }

    public void setProfileH264High() throws IOException {
        mMime = MediaHelper.MIME_TYPE_AVC;
        mBitRate = (int) (mHeight * mWidth * 0.5);
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("high-264-");
    }

    public void setProfileH265Normal() throws IOException {
        mMime = MediaHelper.MIME_TYPE_HEVC;
        mBitRate = mHeight * mWidth * 2;
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("normal-265-");
    }

    public void setProfileH265Medium() throws IOException {
        mMime = MediaHelper.MIME_TYPE_HEVC;
        mBitRate = mHeight * mWidth;
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("medium-265-");
    }

    public void setProfileH265High() throws IOException {
        mMime = MediaHelper.MIME_TYPE_HEVC;
        mBitRate = (int) (mHeight * mWidth * 0.5);
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("high-265-");
    }

    public void setInput(HandleVideo video) {
        mInput = new HandleVideo(video.getUri());
        getInputFormatInfo(video);
    }

    private void setOutput(String type) throws IOException {

        realName = type + "-" + mInputName + ".mp4";

        File outputTempFile = new File(dir, "temp-" + realName);
        if (!outputTempFile.exists()) {
            outputTempFile.createNewFile();
        }
        mOutputTempUri = Uri.fromFile(outputTempFile);

        File outputRealFile = new File(outputDir, realName);
        if (!outputRealFile.exists()) {
            outputRealFile.createNewFile();
        }
        mOutputRealUri = Uri.fromFile(outputRealFile);
    }

    private void getInputFormatInfo(HandleVideo video) {
        String inputPath = mInput.getUri().getPath();
        mInputName = inputPath.substring(inputPath.lastIndexOf('/') + 1, inputPath.lastIndexOf('.'));

        MediaExtractor extractor = setupExtractorForVideo(video);

        assert extractor != null;
        int trackIndex = getVideoTrackIndex(extractor);
        extractor.selectTrack(trackIndex);
        mInputVideoFormat = extractor.getTrackFormat(trackIndex);

        mRotation = mInputVideoFormat.containsKey(MediaFormat.KEY_ROTATION) ? mInputVideoFormat.getInteger(MediaFormat.KEY_ROTATION) : 0;
        mHeight = mInputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);
        mWidth = mInputVideoFormat.getInteger(MediaFormat.KEY_WIDTH);

        trackIndex = getAudioTrackIndex(extractor);
        extractor.selectTrack(trackIndex);
        mInputAudioFormat = extractor.getTrackFormat(trackIndex);
    }

    public void start() throws Throwable {
        VideoEditWrapper wrapper = new VideoEditWrapper();
        Thread th = new Thread(wrapper, "codec test");
        th.join();
        th.start();
        if (wrapper.mThrowable != null) {
            throw wrapper.mThrowable;
        }
    }

    private void compressVideo() {

        setupMuxer();
        setupEncoder();

        feedVideoToEncoder(mInput);

        mVideoEncoder.signalEndOfInputStream();

        releaseOutputResources();
    }

    private void setupMuxer() {

        try {
            mMuxer = new MediaMuxer(mOutputTempUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }
    }

    private void setupEncoder() {
        try {

            MediaFormat outputFormat = MediaFormat.createVideoFormat(mMime, mWidth, mHeight);

            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD);
            }


            mVideoEncoder = MediaCodec.createEncoderByType(mMime);
            mAudioEncoder = MediaCodec.createEncoderByType(mInputAudioFormat.getString(MediaFormat.KEY_MIME));

            mVideoEncoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            mInputAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128 * 1024);
            mAudioEncoder.configure(mInputAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            mInputSurface = new InputSurface(mVideoEncoder.createInputSurface());
            mInputSurface.makeCurrent();

            mVideoEncoder.start();
            mAudioEncoder.start();
        } catch (Exception e) {
            Log.e(TAG, "setupEncoder: " + e);
            e.printStackTrace();
        }
    }

    private void feedVideoToEncoder(HandleVideo video) {

        mLastVideoSampleTime = 0;
        mLastAudioSampleTime = 0;

        decoderVideo = null;
        decoderAudio = null;

        extractorVideo = setupExtractorForVideo(video);
        extractorAudio = setupExtractorForVideo(video);

        if (extractorVideo == null || extractorAudio == null) {
            return;
        }

        int trackIndexVideo = getVideoTrackIndex(extractorVideo);
        extractorVideo.selectTrack(trackIndexVideo);

        MediaFormat videoFormat = extractorVideo.getTrackFormat(trackIndexVideo);

        int trackIndexAudio = getAudioTrackIndex(extractorAudio);
        extractorAudio.selectTrack(trackIndexAudio);

        if (video.getStartTime() != -1) {
            extractorVideo.seekTo(video.getStartTime() * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            extractorAudio.seekTo(video.getStartTime() * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            video.setStartTime(extractorVideo.getSampleTime() / 1000);
        }

        try {
            decoderVideo = MediaCodec.createDecoderByType(MediaHelper.MIME_TYPE_AVC);
            decoderAudio = MediaCodec.createDecoderByType(mInputAudioFormat.getString(MediaFormat.KEY_MIME));
            mOutputSurface = new OutputSurface(mRotation);

            decoderVideo.configure(videoFormat, mOutputSurface.getSurface(), null, 0);
            decoderAudio.configure(mInputAudioFormat, null, null, 0);
            decoderVideo.start();
            decoderAudio.start();

            compressVideo(extractorVideo, decoderVideo, extractorAudio, decoderAudio, video);


        } catch (IOException e) {
            Log.e(TAG, "feedVideoToEncoder: " + e);
            e.printStackTrace();
        }
    }

    private void compressVideo(MediaExtractor extractorVideo, MediaCodec decoderVideo,
                               MediaExtractor extractorAudio, MediaCodec decoderAudio, HandleVideo video) {

        //Video
        MediaCodec.BufferInfo infoVideo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo infoAudio = new MediaCodec.BufferInfo();


        long endTime = video.getEndTime();

        if (endTime == -1) {
            endTime = video.getVideoDuration();
        }

        boolean outputVideoDoneNextTimeWeCheck = false;
        boolean outputAudioDoneNextTimeWeCheck = false;

        boolean outputVideoDone = false;
        boolean outputAudioDone = false;
        boolean inputVideoDone = false;
        boolean inputAudioDone = false;
        boolean decoderVideoDone = false;
        boolean decoderAudioDone = false;

        while (!outputVideoDone) {

            if (!inputVideoDone) {
                int inputBufIndex = decoderVideo.dequeueInputBuffer(TIMEOUT_USE);
                if (inputBufIndex >= 0) {
                    if (extractorVideo.getSampleTime() / 1000 >= endTime) {
                        decoderVideo.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputVideoDone = true;
                    } else {
                        ByteBuffer inputBuf = decoderVideo.getInputBuffer(inputBufIndex);
                        inputBuf.clear();

                        int sampleSize = extractorVideo.readSampleData(inputBuf, 0);
                        if (sampleSize < 0) {
                            decoderVideo.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        } else {
                            decoderVideo.queueInputBuffer(inputBufIndex, 0, sampleSize, extractorVideo.getSampleTime(), 0);
                            extractorVideo.advance();
                        }
                    }
                }
            }

            boolean decoderVideoOutputAvailable = !decoderVideoDone;
            boolean encoderVideoOutputAvailable = true;
            while (decoderVideoOutputAvailable || encoderVideoOutputAvailable) {
                int encoderStatus = mVideoEncoder.dequeueOutputBuffer(infoVideo, TIMEOUT_USE);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    encoderVideoOutputAvailable = false;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                    MediaFormat newFormat = mVideoEncoder.getOutputFormat();
                    mTrackVideoIndex = mMuxer.addTrack(newFormat);
                    mTrackAudioIndex = mMuxer.addTrack(mInputAudioFormat);

                    mMuxer.setOrientationHint(mRotation);
                    mMuxer.start();

                } else if (encoderStatus < 0) {
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(encoderStatus);
                    if (encodedData == null) {
                    }
                    if (infoVideo.size != 0) {
                        encodedData.position(infoVideo.offset);
                        encodedData.limit(infoVideo.offset + infoVideo.size);

                        mMuxer.writeSampleData(mTrackVideoIndex, encodedData, infoVideo);

                    }
                    outputVideoDone = (infoVideo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (outputVideoDone) {
                        decoderVideoOutputAvailable = false;
                        encoderVideoOutputAvailable = false;
                    }

                    mVideoEncoder.releaseOutputBuffer(encoderStatus, false);
                }

                if (outputVideoDoneNextTimeWeCheck) {
                    outputVideoDone = true;
                    decoderVideoOutputAvailable = false;
                    encoderVideoOutputAvailable = false;
                }

                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue;
                }
                if (!decoderVideoDone) {
                    int decoderStatus = decoderVideo.dequeueOutputBuffer(infoVideo, TIMEOUT_USE);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        decoderVideoOutputAvailable = false;
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = decoderVideo.getOutputFormat();

                    } else if (decoderStatus < 0) {
                    } else { // decoderStatus >= 0
                        boolean doRender = (infoVideo.size != 0);

                        decoderVideo.releaseOutputBuffer(decoderStatus, doRender);
                        if (doRender) {
                            mOutputSurface.awaitNewImage();
                            mOutputSurface.drawImage();

                            long nSecs = infoVideo.presentationTimeUs * 1000;

                            if (video.getStartTime() != -1) {
                                nSecs = (infoVideo.presentationTimeUs - (video.getStartTime() * 1000)) * 1000;
                            }

                            Log.d("Running", "Setting presentation time " + nSecs / (1000 * 1000));
                            nSecs = Math.max(0, nSecs);

                            mEncoderVideoPresentationTimeUs += (nSecs - mLastVideoSampleTime);

                            mLastVideoSampleTime = nSecs;

                            mInputSurface.setPresentationTime(mEncoderVideoPresentationTimeUs);

                            mInputSurface.swapBuffers();
                        }
                        if ((infoVideo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputVideoDoneNextTimeWeCheck = true;
                        }
                    }
                }
            }


        }

    }

    private void muxAudioVsVideo() {

        //Video
        MediaCodec.BufferInfo infoAudio = new MediaCodec.BufferInfo();

        long endTime = mInput.getEndTime();

        if (endTime == -1) {
            endTime = mInput.getVideoDuration();
        }

        boolean outputAudioDoneNextTimeWeCheck = false;

        boolean outputAudioDone = false;
        boolean inputAudioDone = false;
        boolean decoderAudioDone = false;

        while (!outputAudioDone) {

            if (!inputAudioDone) {
                int inputBufIndex = decoderAudio.dequeueInputBuffer(TIMEOUT_USE);
                if (inputBufIndex >= 0) {
                    if (extractorAudio.getSampleTime() / 1000 >= endTime) {
                        decoderAudio.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputAudioDone = true;
                    } else {
                        ByteBuffer inputBuf = decoderAudio.getInputBuffer(inputBufIndex);
                        inputBuf.clear();

                        int sampleSize = extractorAudio.readSampleData(inputBuf, 0);
                        if (sampleSize < 0) {
                            decoderAudio.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputAudioDone = true;
                        } else {
                            decoderAudio.queueInputBuffer(inputBufIndex, 0, sampleSize, extractorAudio.getSampleTime(), 0);
                            extractorAudio.advance();
                        }
                    }
                }
            }

            boolean decoderAudioOutputAvailable = !decoderAudioDone;
            boolean encoderAudioOutputAvailable = true;
            while (decoderAudioOutputAvailable || encoderAudioOutputAvailable) {
                int encoderStatus = mAudioEncoder.dequeueOutputBuffer(infoAudio, TIMEOUT_USE);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    encoderAudioOutputAvailable = false;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                    MediaFormat newFormat = mAudioEncoder.getOutputFormat();

                } else if (encoderStatus < 0) {
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = mAudioEncoder.getOutputBuffer(encoderStatus);
                    if (encodedData == null) {
                    }
                    if (infoAudio.size != 0) {
                        encodedData.position(infoAudio.offset);
                        encodedData.limit(infoAudio.offset + infoAudio.size);

                        mMuxer.writeSampleData(mTrackAudioIndex, encodedData, infoAudio);

                    }
                    outputAudioDone = (infoAudio.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    mAudioEncoder.releaseOutputBuffer(encoderStatus, false);
                }

                if (outputAudioDoneNextTimeWeCheck) {
                    outputAudioDone = true;
                }

                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue;
                }
                if (!decoderAudioDone) {
                    int decoderStatus = decoderAudio.dequeueOutputBuffer(infoAudio, TIMEOUT_USE);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        decoderAudioOutputAvailable = false;
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = decoderAudio.getOutputFormat();

                    } else if (decoderStatus < 0) {
                    } else { // decoderStatus >= 0

                        ByteBuffer outputBuffer = decoderAudio.getOutputBuffer(decoderStatus);

                        boolean doRender = (infoAudio.size != 0);

                        if (doRender) {

                            int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);

                            if (inputBufferIndex >= 0) {
                                ByteBuffer inputBuffer = mAudioEncoder.getInputBuffer(inputBufferIndex);
                                inputBuffer.clear();
                                inputBuffer.put(outputBuffer);

                                mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, infoAudio.size, infoAudio.presentationTimeUs, 0);
                            }

                        }
                        decoderAudio.releaseOutputBuffer(decoderStatus, false);

                        if ((infoAudio.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputAudioDoneNextTimeWeCheck = true;
                        }
                    }
                }
            }

        }
        MediaScannerConnection.scanFile(mContext, new String[]{mOutputTempUri.getPath()}, null, null);

        mCompressListener.onSuccess(mOutputTempUri);

        releaseResources();

    }

    private void muxAudioAndVideo() {

        try {

            //Video Extractor
            MediaExtractor videoExtractor = setupExtractorForVideo(new HandleVideo(mOutputTempUri));

            int trackIndexVideo = getVideoTrackIndex(videoExtractor);

            videoExtractor.selectTrack(trackIndexVideo);

            MediaFormat videoFormat = videoExtractor.getTrackFormat(trackIndexVideo);

            Log.e("ProblemMetChetMe", "Temp format: " + videoFormat);

            //Audio Extractor
            MediaExtractor audioExtractor = setupExtractorForVideo(mInput);

            int trackIndexAudio = getAudioTrackIndex(audioExtractor);

            audioExtractor.selectTrack(trackIndexAudio);

            MediaFormat audioFormat = audioExtractor.getTrackFormat(trackIndexAudio);

            //Muxer
            MediaMuxer muxer = new MediaMuxer(mOutputRealUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoIndex = muxer.addTrack(videoFormat);
            int audioIndex = muxer.addTrack(audioFormat);

            muxer.setOrientationHint(mRotation);


            boolean sawEOS = false;
            int offset = 100;
            int sampleSize = 256 * 1024;
            ByteBuffer videoBuf = ByteBuffer.allocate(sampleSize);
            ByteBuffer audioBuf = ByteBuffer.allocate(sampleSize);
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();


            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            muxer.start();

            while (!sawEOS) {
                videoBufferInfo.offset = offset;
                videoBufferInfo.size = videoExtractor.readSampleData(videoBuf, offset);


                if (videoBufferInfo.size < 0 || audioBufferInfo.size < 0) {
                    sawEOS = true;
                    videoBufferInfo.size = 0;

                } else {
                    videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                    videoBufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    muxer.writeSampleData(videoIndex, videoBuf, videoBufferInfo);
                    videoExtractor.advance();

                }
            }


            boolean sawEOS2 = false;
            while (!sawEOS2) {

                audioBufferInfo.offset = offset;
                audioBufferInfo.size = audioExtractor.readSampleData(audioBuf, offset);

                if (videoBufferInfo.size < 0 || audioBufferInfo.size < 0) {
                    sawEOS2 = true;
                    audioBufferInfo.size = 0;
                } else {
                    audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                    audioBufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    muxer.writeSampleData(audioIndex, audioBuf, audioBufferInfo);
                    audioExtractor.advance();
                }
            }


            File temp = new File(mOutputTempUri.getPath());
            if (temp.exists())
                temp.delete();

            muxer.stop();
            muxer.release();

            MediaScannerConnection.scanFile(mContext, new String[]{mOutputRealUri.getPath()}, null, null);

            mCompressListener.onSuccess(mOutputRealUri);

            releaseResources();

        } catch (Exception e) {
            Log.e(TAG, "Problem: " + e);
            e.printStackTrace();
        }


    }

    private void releaseOutputResources() {

        if (mInputSurface != null) {
            mInputSurface.release();
        }

        if (mVideoEncoder != null) {
            mVideoEncoder.stop();
            mVideoEncoder.release();
        }

        if (mOutputSurface != null) {
            mOutputSurface.release();
        }

    }

    private void releaseResources() {


        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
        }

        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }

        if (decoderVideo != null) {
            decoderVideo.stop();
            decoderVideo.release();
        }

        if (extractorVideo != null) {
            extractorVideo.release();
        }

        if (decoderAudio != null) {
            decoderAudio.stop();
            decoderAudio.release();
        }

        if (extractorAudio != null) {
            extractorAudio.release();
        }

    }

    private MediaExtractor setupExtractorForVideo(HandleVideo video) {

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(video.getUri().toString());
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

    private int getAudioTrackIndex(MediaExtractor extractor) {

        for (int trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++) {
            MediaFormat format = extractor.getTrackFormat(trackIndex);

            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null) {
                if (mime.startsWith("audio/")) {
                    return trackIndex;
                }
            }
        }
        return -1;
    }

    public interface CompressListener {
        void onStart();

        void onSuccess(Uri uri);

        void onFail();

        void onProgress(float percent);
    }

    private class VideoEditWrapper implements Runnable {
        private Throwable mThrowable;

        @Override
        public void run() {
            try {
                compressVideo();

                muxAudioVsVideo();

            } catch (Throwable th) {
                mThrowable = th;
            }
        }

    }

}