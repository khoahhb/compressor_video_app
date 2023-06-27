package com.example.compress_video_app.compressor;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.example.compress_video_app.models.HandleVideo;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;


public class VideoCompressor {

    public static final int WIDTH_720P = 1280;
    public static final int HEIGHT_720P = 720;
    public static final int BITRATE_720P = 6000000;
    public static final int FPS_30 = 30; // 30fps
    public static final int FPS_15 = 15; // 15fps
    public static final int IFRAME_INTERVAL_10 = 10;
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture, vTextureCoord).rbga;\n" +
                    "}\n";
    private static final String TAG = "VideoCompressor";
    private static final boolean WORK_AROUND_BUGS = false; // avoid fatal codec bugs
    private static final boolean VERBOSE = false; // lots of logging
    private static final int TIMEOUT_USE = 0;

    private final File internalDir;
    private final File externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    private final NotificationManagerCompat notificationManager;
    private final Context mContext;
    private final CompressListener mCompressListener;
    private final long mAudioEncoderPresentationTimeUs = 0;
    private File chosenDir;
    private String mMime = MediaHelper.MIME_TYPE_AVC;
    private int mWidth = WIDTH_720P;
    private int mHeight = HEIGHT_720P;
    private int mRotation = 0;
    private int mBitRate = BITRATE_720P;
    private int mFrameRate = FPS_30;
    private int mIFrameInterval = IFRAME_INTERVAL_10;
    private boolean isAudioCompress = false; // compress audio
    private HandleVideo mInput = null;
    private String mInputName;
    private String realName;
    private Uri mOutputTempVideoUri;
    private Uri mOutputTempAudioUri;
    private Uri mOutputRealUri;
    private MediaExtractor mVideoExtractor;
    private MediaExtractor mAudioExtractor;
    private MediaFormat mVideoInputFormat;
    private MediaFormat mAudioInputFormat;
    private InputSurface mInputSurface;
    private OutputSurface mOutputSurface;
    private MediaMuxer mVideoMuxer = null;
    private MediaMuxer mAudioMuxer = null;
    private MediaCodec mVideoDecoder;
    private MediaCodec mAudioDecoder;
    private MediaCodec mVideoEncoder = null;
    private MediaCodec mAudioEncoder = null;
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private long mVideoLastSampleTime = 0;
    private long mAudioLastSampleTime = 0;
    private long mVideoEncoderPresentationTimeUs = 0;
    private int mFramesToSkip = 1;

    public VideoCompressor(Context context, CompressListener compressListener) {
        this.mCompressListener = compressListener;
        this.mContext = context;
        notificationManager = NotificationManagerCompat.from(this.mContext);
        internalDir = new File(mContext.getFilesDir(), "temp_videos");
        if (!internalDir.exists()) {
            internalDir.mkdir();
        }
        chosenDir = internalDir;
    }

    public void setCodecH264() {
        mMime = MediaHelper.MIME_TYPE_AVC;
    }

    public void setCodecH265() {
        mMime = MediaHelper.MIME_TYPE_HEVC;
    }

    public void setPercent(int percent) throws IOException {
        mBitRate = mInput.getBitrate() * percent / 105;
        mIFrameInterval = 10;
        String prefix = (mMime == MediaHelper.MIME_TYPE_AVC) ? percent + "-percent-264-" : percent + "-percent-265-";
        setOutput(prefix);
    }

    public void setTargetSize(float size, Context context) throws IOException {
        int percent = (int) (size * 100 / mInput.getFloatSize(context));
        mBitRate = mInput.getBitrate() * percent / 115;
        mIFrameInterval = 10;
        String prefix = (mMime == MediaHelper.MIME_TYPE_AVC) ? size + "-size-264-" : size + "-size-265-";
        setOutput(prefix);
    }

    public void setProfileNormal() throws IOException {
        mBitRate = mHeight * mWidth * 2;
        mIFrameInterval = 10;
        String prefix = (mMime == MediaHelper.MIME_TYPE_AVC) ? "profile-normal-264" : "profile-normal-265";
        setOutput(prefix);
    }

    public void setProfileMedium() throws IOException {
        mBitRate = mHeight * mWidth;
        mIFrameInterval = 10;
        String prefix = (mMime == MediaHelper.MIME_TYPE_AVC) ? "profile-medium-264" : "profile-medium-265";
        setOutput(prefix);
    }

    public void setProfileHigh() throws IOException {
        mBitRate = (int) (mHeight * mWidth * 0.5);
        mIFrameInterval = 10;
        String prefix = (mMime == MediaHelper.MIME_TYPE_AVC) ? "profile-high-264" : "profile-high-265";
        setOutput(prefix);
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

    public void isSaveInternal(boolean is) {
        if (is)
            chosenDir = internalDir;
        else
            chosenDir = externalDir;
    }

    public void isAudioCompress(boolean is) {
        isAudioCompress = is;
    }


    public void setInput(HandleVideo video) {
        mInput = new HandleVideo(video.getUri());
        getInputFormatInfo(video);
    }

    private void setOutput(String type) throws IOException {

        realName = type + "-" + mInputName + ".mp4";

        File outputFileV = new File(internalDir, "tempV-" + realName);
        if (!outputFileV.exists()) {
            outputFileV.createNewFile();
        }
        mOutputTempVideoUri = Uri.fromFile(outputFileV);

        File outputFileA = new File(internalDir, "tempA-" + realName);
        if (!outputFileA.exists()) {
            outputFileA.createNewFile();
        }
        mOutputTempAudioUri = Uri.fromFile(outputFileA);

        File chosenFile = new File(chosenDir, realName);
        if (!chosenFile.exists()) {
            chosenFile.createNewFile();
        }
        mOutputRealUri = Uri.fromFile(chosenFile);

    }

    private void getInputFormatInfo(HandleVideo video) {
        String inputPath = mInput.getUri().getPath();
        mInputName = inputPath.substring(inputPath.lastIndexOf('/') + 1, inputPath.lastIndexOf('.'));

        MediaExtractor extractor = setupExtractorByHandleVideo(video);

        assert extractor != null;
        int trackIndex = getVideoTrackIndex(extractor);
        extractor.selectTrack(trackIndex);
        mVideoInputFormat = extractor.getTrackFormat(trackIndex);

        mRotation = mVideoInputFormat.containsKey(MediaFormat.KEY_ROTATION) ? mVideoInputFormat.getInteger(MediaFormat.KEY_ROTATION) : 0;
        mHeight = (mVideoInputFormat.getInteger(MediaFormat.KEY_HEIGHT) > 2560) ? 2560 : mVideoInputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        mWidth = (mVideoInputFormat.getInteger(MediaFormat.KEY_WIDTH) > 1440) ? 1440 : mVideoInputFormat.getInteger(MediaFormat.KEY_WIDTH);
        mFramesToSkip = (mInput.getFrameRate() > 30) ? 2 : 1;
        mFrameRate = (mInput.getFrameRate() > 30) ? mInput.getFrameRate() / 2 : mInput.getFrameRate();

        trackIndex = getAudioTrackIndex(extractor);
        extractor.selectTrack(trackIndex);
        mAudioInputFormat = extractor.getTrackFormat(trackIndex);
    }

    public void start() throws Throwable {
        CompletableFuture<Void> compressVideoFuture = CompletableFuture.runAsync(startCompressVideo());
        CompletableFuture<Void> compressAudioFuture = CompletableFuture.runAsync(startCompressAudio());

        CompletableFuture<Void> muxingFuture = CompletableFuture.allOf(compressVideoFuture, compressAudioFuture)
                .thenRun(startMuxAudioVideo());
    }

    public Runnable startCompressVideo() throws Throwable {
        VideoEditWrapper wrapper = new VideoEditWrapper();
        return wrapper;
    }

    public Runnable startCompressAudio() throws Throwable {
        AudioEditWrapper wrapper = new AudioEditWrapper();
        return wrapper;
    }

    public Runnable startMuxAudioVideo() throws Throwable {
        MuxEditWrapper wrapper = new MuxEditWrapper();
        return wrapper;
    }

    private void compressVideo() {

        setupVideoMuxer();

        setupVideoEncoder();

        setupVideoDecoder();

        compressVideoInput();

        mVideoEncoder.signalEndOfInputStream();

        releaseVideoOutputResources();
    }

    private void compressAudio() {

        setupAudioMuxer();

        setupAudioEncoder();

        setupAudioDecoder();

        compressAudioInput();

        releaseAudioResources();
    }

    private void setupVideoMuxer() {

        try {
            mVideoMuxer = new MediaMuxer(mOutputTempVideoUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("VideoMediaMuxer creation failed", ioe);
        }
    }

    private void setupAudioMuxer() {

        try {
            mAudioMuxer = new MediaMuxer(mOutputTempAudioUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("AudioMediaMuxer creation failed", ioe);
        }
    }

    private void setupVideoEncoder() {
        try {

            MediaFormat outputFormat = MediaFormat.createVideoFormat(mMime, mWidth, mHeight);

            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD);
                outputFormat.setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, 1);
            }

            mVideoEncoder = MediaCodec.createEncoderByType(mMime);

            mVideoEncoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Log.d("MetMoi", "Video format: " + outputFormat);

            mInputSurface = new InputSurface(mVideoEncoder.createInputSurface());
            mInputSurface.makeCurrent();

            mVideoEncoder.start();
        } catch (Exception e) {
            Log.e(TAG, "setupVideoEncoder: " + e);
            e.printStackTrace();
        }
    }

    private void setupAudioEncoder() {
        try {

            mAudioEncoder = MediaCodec.createEncoderByType(mAudioInputFormat.getString(MediaFormat.KEY_MIME));

            mAudioInputFormat.setInteger(MediaFormat.KEY_BIT_RATE, mAudioInputFormat.getInteger(MediaFormat.KEY_BIT_RATE) * 4 / 10);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mAudioInputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD);
            }
            mAudioEncoder.configure(mAudioInputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            mAudioEncoder.start();
        } catch (Exception e) {
            Log.e(TAG, "setupAudioEncoder: " + e);
            e.printStackTrace();
        }
    }

    private void setupVideoDecoder() {

        //Video decoder
        mVideoLastSampleTime = 0;
        mVideoDecoder = null;
        mVideoExtractor = setupExtractorByHandleVideo(mInput);

        if (mVideoExtractor == null) {
            return;
        }

        int trackIndexVideo = getVideoTrackIndex(mVideoExtractor);

        mVideoExtractor.selectTrack(trackIndexVideo);
        MediaFormat videoFormat = mVideoExtractor.getTrackFormat(trackIndexVideo);

        if (mInput.getStartTime() != -1) {
            mVideoExtractor.seekTo(mInput.getStartTime() * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            mInput.setStartTime(mVideoExtractor.getSampleTime() / 1000);
        }

        try {
            //Start video decoder
            mVideoDecoder = MediaCodec.createDecoderByType(MediaHelper.MIME_TYPE_AVC);
            mOutputSurface = new OutputSurface(mRotation);
            mVideoDecoder.configure(videoFormat, mOutputSurface.getSurface(), null, 0);
            mVideoDecoder.start();

        } catch (IOException e) {
            Log.e(TAG, "setupVideoDecoder: " + e);
            e.printStackTrace();
        }
    }

    private void setupAudioDecoder() {


        //Audio decoder
        mAudioLastSampleTime = 0;
        mAudioDecoder = null;
        mAudioExtractor = setupExtractorByHandleVideo(mInput);

        if (mAudioExtractor == null) {
            return;
        }

        int trackIndexAudio = getAudioTrackIndex(mAudioExtractor);

        mAudioExtractor.selectTrack(trackIndexAudio);
        if (mInput.getStartTime() != -1) {
            mAudioExtractor.seekTo(mInput.getStartTime() * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            mInput.setStartTime(mVideoExtractor.getSampleTime() / 1000);
        }

        try {
            //Start audio decoder
            mAudioDecoder = MediaCodec.createDecoderByType(mAudioInputFormat.getString(MediaFormat.KEY_MIME));
            mAudioDecoder.configure(mAudioInputFormat, null, null, 0);
            mAudioDecoder.start();
        } catch (IOException e) {
            Log.e(TAG, "setupAudioDecoder: " + e);
            e.printStackTrace();
        }
    }

    private void compressVideoInput() {

        //Video
        MediaCodec.BufferInfo infoVideo = new MediaCodec.BufferInfo();


        long endTime = mInput.getEndTime();

        if (endTime == -1) {
            endTime = mInput.getDuration();
        }

        boolean outputVideoDoneNextTimeWeCheck = false;

        boolean outputVideoDone = false;
        boolean inputVideoDone = false;
        boolean decoderVideoDone = false;

        int frameCount = 0;

        while (!outputVideoDone) {
            if (!inputVideoDone) {
                int inputBufIndex = mVideoDecoder.dequeueInputBuffer(TIMEOUT_USE);
                if (inputBufIndex >= 0) {
                    if (mVideoExtractor.getSampleTime() / 1000 >= endTime) {
                        mVideoDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputVideoDone = true;
                    } else {
                        ByteBuffer inputBuf = mVideoDecoder.getInputBuffer(inputBufIndex);
                        inputBuf.clear();

                        int sampleSize = mVideoExtractor.readSampleData(inputBuf, 0);
                        if (sampleSize < 0) {
                            mVideoDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        } else {
                            mVideoDecoder.queueInputBuffer(inputBufIndex, 0, sampleSize, mVideoExtractor.getSampleTime(), 0);

                            mVideoExtractor.advance();

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
                    Log.d("MetMoi", "Video format change: " + newFormat);

                    mVideoTrackIndex = mVideoMuxer.addTrack(newFormat);
                    mVideoMuxer.setOrientationHint(mRotation);
                    mVideoMuxer.start();

                } else if (encoderStatus < 0) {
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(encoderStatus);
                    if (encodedData == null) {
                    }
                    if (infoVideo.size != 0) {
                        encodedData.position(infoVideo.offset);
                        encodedData.limit(infoVideo.offset + infoVideo.size);

                        mVideoMuxer.writeSampleData(mVideoTrackIndex, encodedData, infoVideo);

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
                    int decoderStatus = mVideoDecoder.dequeueOutputBuffer(infoVideo, TIMEOUT_USE);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        decoderVideoOutputAvailable = false;
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = mVideoDecoder.getOutputFormat();

                    } else if (decoderStatus < 0) {
                    } else { // decoderStatus >= 0
                        boolean doRender = (infoVideo.size != 0);

                        mVideoDecoder.releaseOutputBuffer(decoderStatus, doRender);
                        if (doRender) {

                            frameCount++;

                            if (frameCount % mFramesToSkip == 0) {
                                mOutputSurface.awaitNewImage();
                                mOutputSurface.drawImage();

                                long nSecs = infoVideo.presentationTimeUs * 1000;

                                if (mInput.getStartTime() != -1) {
                                    nSecs = (infoVideo.presentationTimeUs - (mInput.getStartTime() * 1000)) * 1000;
                                }

                                Log.d("Running", "Setting presentation time " + nSecs / (1000 * 1000));

                                float presentation = 0;
                                float total = 0;

                                presentation = (int) ((nSecs / (1000 * 1000)));
                                total = mInput.getDuration();

                                int percent = (int) ((presentation / total) * 100);

                                mCompressListener.onProgress(percent);


                                nSecs = Math.max(0, nSecs);

                                mVideoEncoderPresentationTimeUs += (nSecs - mVideoLastSampleTime);

                                mVideoLastSampleTime = nSecs;

                                mInputSurface.setPresentationTime(mVideoEncoderPresentationTimeUs);

                                mInputSurface.swapBuffers();
                            }

                        }
                        if ((infoVideo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputVideoDoneNextTimeWeCheck = true;
                        }
                    }
                }
            }
        }
        releaseVideoResources();
    }

    private void compressAudioInput() {

        //Video
        MediaCodec.BufferInfo infoAudio = new MediaCodec.BufferInfo();

        long endTime = mInput.getEndTime();

        if (endTime == -1) {
            endTime = mInput.getDuration();
        }
        boolean outputAudioDoneNextTimeWeCheck = false;

        boolean outputAudioDone = false;
        boolean inputAudioDone = false;
        boolean decoderAudioDone = false;

        while (!outputAudioDone) {

            if (!inputAudioDone) {
                int inputBufIndex = mAudioDecoder.dequeueInputBuffer(TIMEOUT_USE);
                if (inputBufIndex >= 0) {
                    if (mAudioExtractor.getSampleTime() / 1000 >= endTime) {
                        mAudioDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputAudioDone = true;
                    } else {
                        ByteBuffer inputBuf = mAudioDecoder.getInputBuffer(inputBufIndex);
                        inputBuf.clear();

                        int sampleSize = mAudioExtractor.readSampleData(inputBuf, 0);
                        if (sampleSize < 0) {
                            mAudioDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputAudioDone = true;
                        } else {
                            mAudioDecoder.queueInputBuffer(inputBufIndex, 0, sampleSize, mAudioExtractor.getSampleTime(), 0);
                            mAudioExtractor.advance();
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
                    mAudioTrackIndex = mAudioMuxer.addTrack(newFormat);
                    mAudioMuxer.setOrientationHint(mRotation);
                    mAudioMuxer.start();

                } else if (encoderStatus < 0) {
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = mAudioEncoder.getOutputBuffer(encoderStatus);
                    if (encodedData == null) {
                    }
                    if (infoAudio.size != 0) {
                        encodedData.position(infoAudio.offset);
                        encodedData.limit(infoAudio.offset + infoAudio.size);

                        mAudioMuxer.writeSampleData(mAudioTrackIndex, encodedData, infoAudio);

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
                    int decoderStatus = mAudioDecoder.dequeueOutputBuffer(infoAudio, TIMEOUT_USE);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        decoderAudioOutputAvailable = false;
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = mAudioDecoder.getOutputFormat();

                    } else if (decoderStatus < 0) {
                    } else { // decoderStatus >= 0

                        ByteBuffer outputBuffer = mAudioDecoder.getOutputBuffer(decoderStatus);

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
                        mAudioDecoder.releaseOutputBuffer(decoderStatus, false);

                        if ((infoAudio.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputAudioDoneNextTimeWeCheck = true;
                        }
                    }
                }
            }

        }
        releaseAudioResources();
    }

    private void releaseVideoOutputResources() {

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

    private void releaseAudioOutputResources() {
        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
        }
    }

    private void releaseVideoResources() {

        if (mVideoMuxer != null) {
            mVideoMuxer.stop();
            mVideoMuxer.release();
            mVideoMuxer = null;
        }

        if (mVideoDecoder != null) {
            mVideoDecoder.stop();
            mVideoDecoder.release();
        }

        if (mVideoExtractor != null) {
            mVideoExtractor.release();
        }

    }

    private void releaseAudioResources() {

        if (mAudioMuxer != null) {
            mAudioMuxer.stop();
            mAudioMuxer.release();
            mAudioMuxer = null;
        }

        if (mAudioDecoder != null) {
            mAudioDecoder.stop();
            mAudioDecoder.release();
        }

        if (mAudioExtractor != null) {
            mAudioExtractor.release();
        }

    }

    private void muxAudioAndVideo() {

        try {
            //Video Extractor
            MediaExtractor videoExtractor = setupExtractorByHandleVideo(new HandleVideo(mOutputTempVideoUri));

            int trackIndexVideo = getVideoTrackIndex(videoExtractor);

            videoExtractor.selectTrack(trackIndexVideo);

            MediaFormat videoFormat = videoExtractor.getTrackFormat(trackIndexVideo);

            Log.d("MetMoi", "Video format mux: " + videoFormat);


            //Audio Extractor
            MediaExtractor audioExtractor = setupExtractorByUri(mOutputTempAudioUri);

            int trackIndexAudio = getAudioTrackIndex(audioExtractor);

            audioExtractor.selectTrack(trackIndexAudio);

            MediaFormat audioFormat = audioExtractor.getTrackFormat(trackIndexAudio);
            Log.d(TAG + "test", "audioFormat: " + audioFormat);

            //Muxer
            MediaMuxer muxer = new MediaMuxer(mOutputRealUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoIndex = muxer.addTrack(videoFormat);
            int audioIndex = muxer.addTrack(audioFormat);

            muxer.setOrientationHint(mRotation);


            boolean sawEOS = false;
            int offset = 0;
            int sampleSize = 256 * 1024;
            ByteBuffer videoBuf = ByteBuffer.allocate(sampleSize);
            ByteBuffer audioBuf = ByteBuffer.allocate(sampleSize);
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

            if (mInput.getStartTime() != -1) {
                videoExtractor.seekTo(mInput.getStartTime() * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                audioExtractor.seekTo(mInput.getStartTime() * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                mInput.setStartTime(mAudioExtractor.getSampleTime() / 1000);
            }

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

            muxer.stop();
            muxer.release();

            File tempA = new File(mOutputTempAudioUri.getPath());
            File tempV = new File(mOutputTempVideoUri.getPath());

            if (tempA.exists()) tempA.delete();
            if (tempV.exists()) tempV.delete();

            mCompressListener.onSuccess(mOutputRealUri);

        } catch (Exception e) {
            Log.e("TestMuxAudio", "Problem: " + e);
        }
    }

    private MediaExtractor setupExtractorByHandleVideo(HandleVideo video) {

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(video.getUri().getPath());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return extractor;
    }

    private MediaExtractor setupExtractorByUri(Uri uri) {

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(uri.getPath());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return extractor;
    }

    private int getVideoTrackIndex(MediaExtractor extractor) {
        for (int trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++) {
            MediaFormat format = extractor.getTrackFormat(trackIndex);

            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null) {
                if (mime.startsWith("video/")) {
                    return trackIndex;
                }
            }
        }
        return -1;
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
        void onSuccess(Uri uri);

        void onFail();

        void onProgress(int percent);
    }

    private class VideoEditWrapper implements Runnable {
        private Throwable mThrowable;

        @Override
        public void run() {
            try {
                compressVideo();
            } catch (Throwable th) {
                mThrowable = th;
            }
        }
    }

    private class AudioEditWrapper implements Runnable {
        private Throwable mThrowable;

        @Override
        public void run() {
            try {
                compressAudio();
            } catch (Throwable th) {
                mThrowable = th;
            }
        }
    }

    private class MuxEditWrapper implements Runnable {
        private Throwable mThrowable;

        @Override
        public void run() {
            try {
                muxAudioAndVideo();
            } catch (Throwable th) {
                mThrowable = th;
            }
        }
    }
}