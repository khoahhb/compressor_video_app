package com.example.compress_video_app;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;


public class VideoCompressor  {

    private static final String TAG = "VideoCompressor";

    private static final int TIMEOUT_USEC = 10000;

    public static final int WIDTH_QCIF = 176;
    public static final int HEIGHT_QCIF = 144;
    public static final int BITRATE_QCIF = 1000000;

    public static final int WIDTH_QVGA = 320;
    public static final int HEIGHT_QVGA = 240;
    public static final int BITRATE_QVGA = 2000000;

    public static final int WIDTH_720P = 1280;
    public static final int HEIGHT_720P = 720;
    public static final int BITRATE_720P = 6000000;

    // Avoid fatal codec bugs
    private static final boolean WORK_AROUND_BUGS = false;
    // Show logs
    private static final boolean VERBOSE = false;

    public static final int FPS_30 = 30;
    public static final int FPS_15 = 15;

    public static final int IFRAME_INTERVAL_10 = 10;

    private int mWidth = WIDTH_720P;
    private int mHeight = HEIGHT_720P;
    private int mBitRate = BITRATE_720P;
    private int mFrameRate = FPS_30;
    private int mIFrameInterval = IFRAME_INTERVAL_10;

    private InputVideo mVideo = null;
    private Uri mOutputUri;

    private InputSurface mInputSurface;
    private OutputSurface mOutputSurface;

    private MediaCodec mVideoEncoder = null;
    private MediaCodec mAudioEncoder = null;
    private MediaMuxer mMuxer = null;
    private int mTrackIndexVideo = -1;
    private int mTrackIndexAudio = -1;
    private long mLastSampleTime = 0;
    private long mEncoderPresentationTimeUs = 0;
    private MediaExtractor audioExtractor = null;

    private final VideoCompressorListener videoCompressorListener;

    public interface VideoCompressorListener {
        void onSuccess();
    }


    public VideoCompressor(VideoCompressorListener videoCompressorListener) {
        this.videoCompressorListener = videoCompressorListener;
    }

    public void setInput(InputVideo clip) {
        mVideo = clip;
    }

    public void setOutput(Uri outputUri) {
        mOutputUri = outputUri;
    }

    public void setOutputResolution(int width, int height) {
        if ((width % 16) != 0 || (height % 16) != 0) {
            Log.w(TAG, "WARNING: width or height not multiple of 16");
        }
        mWidth = width;
        mHeight = height;
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

    private void setupEncoder() {
        try {

            audioExtractor = setupExtractorForVideo(mVideo);

            if (audioExtractor == null) {
                return;
            }

            int trackIndex = getAudioTrackIndex(audioExtractor);
            audioExtractor.selectTrack(trackIndex);

            MediaFormat audioFormat = audioExtractor.getTrackFormat(trackIndex);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            audioFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
            audioFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval);

            mAudioEncoder = MediaCodec.createEncoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
            mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAudioEncoder.start();

            MediaFormat outputFormat = MediaFormat.createVideoFormat(MediaHelper.MIME_TYPE_AVC, mWidth, mHeight);
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval);

            mVideoEncoder = MediaCodec.createEncoderByType(MediaHelper.MIME_TYPE_AVC);

            mVideoEncoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = new InputSurface(mVideoEncoder.createInputSurface());
            mInputSurface.makeCurrent();
            mVideoEncoder.start();
        } catch (Exception e) {
            Log.e(TAG, "setupEncoder: " + e);
            e.printStackTrace();
        }
    }

    private void setupMuxer() {
        try {
            mMuxer = new MediaMuxer(mOutputUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }
    }

    private MediaExtractor setupExtractorForVideo(InputVideo clip) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(clip.getUri().toString());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return extractor;
    }

    public void start() throws Throwable {

        VideoEditWrapper wrapper = new VideoEditWrapper();

        Thread th = new Thread(wrapper, "codec test");

        th.start();

        if (wrapper.mThrowable != null) {
            throw wrapper.mThrowable;
        }
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

    private void compressVideo() {

        setupEncoder();

        setupMuxer();

        feedVideoToEncoder(mVideo);

        mVideoEncoder.signalEndOfInputStream();

        releaseOutputResources();
    }

    private void feedVideoToEncoder(InputVideo clip) {

        mLastSampleTime = 0;

        MediaCodec decoder = null;

        MediaExtractor extractor = setupExtractorForVideo(clip);

        if (extractor == null) {
            return;
        }

        int trackIndex = getVideoTrackIndex(extractor);
        extractor.selectTrack(trackIndex);

        MediaFormat clipFormat = extractor.getTrackFormat(trackIndex);

        if (clip.getStartTime() != -1) {
            extractor.seekTo(clip.getStartTime() * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            clip.setStartTime(extractor.getSampleTime() / 1000);
        }

        try {
            decoder = MediaCodec.createDecoderByType(MediaHelper.MIME_TYPE_AVC);
            mOutputSurface = new OutputSurface();

            decoder.configure(clipFormat, mOutputSurface.getSurface(), null, 0);
            decoder.start();

            compressVideo(extractor, decoder, clip);

        } catch (IOException e) {
            Log.e(TAG, "feedClipToEncoder: " + e);
            e.printStackTrace();
        } finally {

            if (mOutputSurface != null) {
                mOutputSurface.release();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }

            if (mVideoEncoder != null) {
                mVideoEncoder.stop();
                mVideoEncoder.release();
            }

            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }
    }

    private void compressVideo(MediaExtractor extractor, MediaCodec decoder, InputVideo clip) {

        ByteBuffer[] audioInputBuffers = mAudioEncoder.getInputBuffers();
        ByteBuffer[] audioOutputBuffers = mAudioEncoder.getOutputBuffers();

        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = mVideoEncoder.getOutputBuffers();

        MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();

        int inputChunk = 0;
        int outputCount = 0;

        long endTime = clip.getEndTime();
        if (endTime == -1) {
            endTime = clip.getVideoDuration();
        }

        boolean outputDoneNextTimeWeCheck = false;
        boolean outputDone = false;
        boolean inputDone = false;
        boolean decoderDone = false;

        while (!outputDone) {
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    if (extractor.getSampleTime() / 1000 >= endTime) {
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        if (VERBOSE)
                            Log.d(TAG, "sent input EOS (with zero-length frame)");
                    } else {
                        ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                        inputBuf.clear();

                        int sampleSize = extractor.readSampleData(inputBuf, 0);
                        if (sampleSize < 0) {
                            Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        } else {
                            Log.d(TAG, "InputBuffer ADVANCING");
                            decoder.queueInputBuffer(inputBufIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                        inputChunk++;
                    }
                }
                int inputAudioBufIndex = mAudioEncoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputAudioBufIndex >= 0) {
                    if (audioExtractor.getSampleTime() / 1000 >= endTime) {
                        mAudioEncoder.queueInputBuffer(inputAudioBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        ByteBuffer inputBuf = audioInputBuffers[inputAudioBufIndex];
                        inputBuf.clear();

                        int sampleSize = audioExtractor.readSampleData(inputBuf, 0);
                        if (sampleSize < 0) {
                            mAudioEncoder.queueInputBuffer(inputAudioBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        } else {
                            mAudioEncoder.queueInputBuffer(inputAudioBufIndex, 0, sampleSize, audioExtractor.getSampleTime(), 0);
                            audioExtractor.advance();
                        }
                    }
                }

            }

            // Assume output is available.
            boolean decoderOutputAvailable = !decoderDone;
            boolean encoderOutputAvailable = true;
            while (decoderOutputAvailable || encoderOutputAvailable) {
                int encoderStatus = mVideoEncoder.dequeueOutputBuffer(videoInfo, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (VERBOSE)
                        Log.d(TAG, "no output from encoder available");
                    encoderOutputAvailable = false;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    encoderOutputBuffers = mVideoEncoder.getOutputBuffers();
                    if (VERBOSE)
                        Log.d(TAG, "encoder output buffers changed");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                    MediaFormat newFormat = mVideoEncoder.getOutputFormat();

                    mTrackIndexVideo = mMuxer.addTrack(newFormat);
                    mMuxer.start();
                    if (VERBOSE)
                        Log.d(TAG, "encoder output format changed: " + newFormat);
                } else if (encoderStatus < 0) {
                } else {

                    // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];

                    if (encodedData == null) {}

                    // Write the data to the output
                    if (videoInfo.size != 0) {

                        encodedData.position(videoInfo.offset);
                        encodedData.limit(videoInfo.offset + videoInfo.size);
                        outputCount++;

                        mMuxer.writeSampleData(mTrackIndexVideo, encodedData, videoInfo);

                        if (VERBOSE)
                            Log.d(TAG, "encoder output " + videoInfo.size + " bytes");
                    }

                    outputDone = (videoInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    mVideoEncoder.releaseOutputBuffer(encoderStatus, false);
                }

                if (outputDoneNextTimeWeCheck) {
                    outputDone = true;
                }

                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue;
                }

                if (!decoderDone) {
                    int decoderStatus = decoder.dequeueOutputBuffer(videoInfo, TIMEOUT_USEC);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (VERBOSE)
                            Log.d(TAG, "no output from decoder available");
                        decoderOutputAvailable = false;
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // decoderOutputBuffers = decoder.getOutputBuffers();
                        if (VERBOSE)
                            Log.d(TAG, "decoder output buffers changed (we don't care)");
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = decoder.getOutputFormat();
                        if (VERBOSE)
                            Log.d(TAG, "decoder output format changed: " + newFormat);
                    } else if (decoderStatus < 0) {
                    } else {

                        // decoderStatus >= 0
                        if (VERBOSE)
                            Log.d(TAG, "surface decoder given buffer " + decoderStatus + " (size=" + videoInfo.size + ")");

                        boolean doRender = (videoInfo.size != 0);

                        decoder.releaseOutputBuffer(decoderStatus, doRender);

                        if (doRender) {
                            if (VERBOSE)
                                Log.d(TAG, "awaiting frame");
                            mOutputSurface.awaitNewImage();
                            mOutputSurface.drawImage();

                            long nSecs = videoInfo.presentationTimeUs * 1000;

                            if (clip.getStartTime() != -1) {
                                nSecs = (videoInfo.presentationTimeUs - (clip.getStartTime() * 1000)) * 1000;
                            }

                            Log.d("this", "Setting presentation time " + nSecs / (1000 * 1000));

                            nSecs = Math.max(0, nSecs);

                            mEncoderPresentationTimeUs += (nSecs - mLastSampleTime);

                            mLastSampleTime = nSecs;

                            mInputSurface.setPresentationTime(mEncoderPresentationTimeUs);

                            if (VERBOSE)
                                Log.d(TAG, "swapBuffers");

                            mInputSurface.swapBuffers();
                        }
                        if ((videoInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDoneNextTimeWeCheck = true;
                        }
                    }
                }
            }
        }
        if (inputChunk != outputCount) {
            // throw new RuntimeException( "frame lost: " + inputChunk + " in, " + outputCount + " out" );
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

        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
        }

        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
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

}