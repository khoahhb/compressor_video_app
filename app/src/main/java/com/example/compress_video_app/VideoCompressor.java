package com.example.compress_video_app;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;


public class VideoCompressor {

    private static final String TAG = "VideoCompressor";

    private final File dir;
    private final Context mContext;

    private static final int TIMEOUT_USE = 0;
    public static final int WIDTH_720P = 1280;
    public static final int HEIGHT_720P = 720;
    public static final int BITRATE_720P = 6000000;

    private static final boolean WORK_AROUND_BUGS = false; // avoid fatal codec bugs
    private static final boolean VERBOSE = false; // lots of logging

    public static final int FPS_30 = 30; // 30fps
    public static final int FPS_15 = 15; // 15fps
    public static final int IFRAME_INTERVAL_10 = 10;

    private String mMime = MediaHelper.MIME_TYPE_AVC;
    private int mWidth = WIDTH_720P;
    private int mHeight = HEIGHT_720P;
    private int mRotation = 0;
    private int mBitRate = BITRATE_720P;
    private int mFrameRate = FPS_30;
    private int mIFrameInterval = IFRAME_INTERVAL_10;

    InputVideo mInput = null;

    private Uri mOutputTempUri;
    private Uri mOutputRealUri;

    private InputSurface mInputSurface;
    private OutputSurface mOutputSurface;

    private MediaMuxer mMuxer = null;
    private MediaCodec mEncoder = null;

    private String mInputName;

    private int mTrackIndex = -1;
    private long mLastSampleTime = 0;
    private long mEncoderPresentationTimeUs = 0;

    private CompressListener mCompressListener;

    public interface CompressListener {
        void onStart();
        void onSuccess(String compressVideoPath);
        void onFail();
        void onProgress(float percent);
    }

    public void setOutputBitRate( int bitRate ) {
        mBitRate = bitRate;
    }

    public void setOutputFrameRate( int frameRate ) {
        mFrameRate = frameRate;
    }

    public void setOutputIFrameInterval( int IFrameInterval ) {
        mIFrameInterval = IFrameInterval;
    }

    public VideoCompressor(Context context, CompressListener compressListener) {
        this.mCompressListener = compressListener;
        this.mContext = context;
        dir = new File(mContext.getFilesDir(), "temp_videos");
        if (!dir.exists()) {
            dir.mkdir();
        }
    }

    public void setProfileH264Normal() throws IOException {
        mMime = MediaHelper.MIME_TYPE_AVC;
        mBitRate = mHeight*mWidth*2;
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("normal");
    }
    public void setProfileH264Medium() throws IOException {
        mMime = MediaHelper.MIME_TYPE_AVC;
        mBitRate = mHeight*mWidth;
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("medium");
    }
    public void setProfileH264High() throws IOException {
        mMime = MediaHelper.MIME_TYPE_AVC;
        mBitRate = (int) (mHeight*mWidth*0.5);
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("high");
    }
    public void setProfileH265Normal() throws IOException {
        mMime = MediaHelper.MIME_TYPE_HEVC;
        mBitRate = mHeight*mWidth*2;
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("normal");
    }
    public void setProfileH265Medium() throws IOException {
        mMime = MediaHelper.MIME_TYPE_HEVC;
        mBitRate = mHeight*mWidth;
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("medium");
    }
    public void setProfileH265High() throws IOException {
        mMime = MediaHelper.MIME_TYPE_HEVC;
        mBitRate = (int) (mHeight*mWidth*0.5);
        mFrameRate = 30;
        mIFrameInterval = 10;
        setOutput("high");
    }

    public void setInput(InputVideo video ) {
        mInput = new InputVideo(video.getUri());
        getInputFormatInfo(video);
    }

    private void setOutput( String type ) throws IOException {

        String realName = type + "-" + mInputName + ".mp4";

        File outputTempFile = new File(dir, "temp-" + realName);
        if (!outputTempFile.exists()) {
            outputTempFile.createNewFile();
        }
        mOutputTempUri = Uri.fromFile(outputTempFile);

        File outputRealFile = new File(dir, realName);
        if (!outputRealFile.exists()) {
            outputRealFile.createNewFile();
        }
        mOutputRealUri = Uri.fromFile(outputRealFile);
    }

    private void getInputFormatInfo(InputVideo video ){
        String inputPath = mInput.getUri().getPath();
        mInputName = inputPath.substring(inputPath.lastIndexOf('/') + 1, inputPath.lastIndexOf('.'));

        MediaExtractor extractor = setupExtractorForVideo(video);

        assert extractor != null;
        int trackIndex = getVideoTrackIndex(extractor);
        extractor.selectTrack( trackIndex );
        MediaFormat mInputFormat = extractor.getTrackFormat(trackIndex);

        mRotation = mInputFormat.containsKey(MediaFormat.KEY_ROTATION) ? mInputFormat.getInteger(MediaFormat.KEY_ROTATION) : 0;
        mHeight = mInputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        mWidth = mInputFormat.getInteger(MediaFormat.KEY_WIDTH);
    }

    public void start() throws Throwable {
        VideoEditWrapper wrapper = new VideoEditWrapper();
        Thread th = new Thread( wrapper, "codec test" );
        th.join();
        th.start();
        if ( wrapper.mThrowable != null ) {
            throw wrapper.mThrowable;
        }
    }

    private class VideoEditWrapper implements Runnable {
        private Throwable mThrowable;

        @Override
        public void run() {
            try {
                compressVideo();

                muxAudioAndVideo();

            } catch ( Throwable th ) {
                mThrowable = th;
            }
        }

    }

    private void compressVideo() {

        setupMuxer();
        setupEncoder();

        feedVideoToEncoder( mInput );

        mEncoder.signalEndOfInputStream();

        releaseOutputResources();
    }

    private void setupMuxer() {

        try {
            mMuxer = new MediaMuxer( mOutputTempUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 );
        } catch ( IOException ioe ) {
            throw new RuntimeException( "MediaMuxer creation failed", ioe );
        }
    }

    private void setupEncoder() {
        try {

            MediaFormat outputFormat = MediaFormat.createVideoFormat( mMime, mWidth, mHeight );

            outputFormat.setInteger( MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface );
            outputFormat.setInteger( MediaFormat.KEY_BIT_RATE, mBitRate );
            outputFormat.setInteger( MediaFormat.KEY_FRAME_RATE, mFrameRate );
            outputFormat.setInteger( MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval );
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD);
            }

            Log.d(TAG+"test", "setupEncoder: " + outputFormat);

            mEncoder = MediaCodec.createEncoderByType(mMime);

            mEncoder.configure( outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE );

            mInputSurface = new InputSurface( mEncoder.createInputSurface() );
            mInputSurface.makeCurrent();

            mEncoder.start();
        } catch (Exception e) {
            Log.e(TAG, "setupEncoder: " + e);
            e.printStackTrace();
        }
    }

    private void feedVideoToEncoder(InputVideo video ) {

        mLastSampleTime = 0;

        MediaCodec decoder = null;

        MediaExtractor extractor = setupExtractorForVideo(video);

        if(extractor == null ) {
            return;
        }

        int trackIndex = getVideoTrackIndex(extractor);
        extractor.selectTrack( trackIndex );

        MediaFormat videoFormat = extractor.getTrackFormat( trackIndex );

        if ( video.getStartTime() != -1 ) {
            extractor.seekTo( video.getStartTime() * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC );
            video.setStartTime( extractor.getSampleTime() / 1000 );
        }

        try {
            decoder = MediaCodec.createDecoderByType( MediaHelper.MIME_TYPE_AVC );
            mOutputSurface = new OutputSurface(mRotation);

            decoder.configure( videoFormat, mOutputSurface.getSurface(), null, 0 );
            decoder.start();

            compressVideo( extractor, decoder, video );


        } catch (IOException e) {
            Log.e(TAG, "feedVideoToEncoder: " + e);
            e.printStackTrace();
        } finally {

            if ( mOutputSurface != null ) {
                mOutputSurface.release();
            }
            if ( decoder != null ) {
                decoder.stop();
                decoder.release();
            }

            if ( extractor != null ) {
                extractor.release();
            }
        }
    }

    private void compressVideo(MediaExtractor extractor, MediaCodec decoder, InputVideo video ) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int outputCount = 0;

        long endTime = video.getEndTime();

        if ( endTime == -1 ) {
            endTime = video.getVideoDuration();
        }

        boolean outputDoneNextTimeWeCheck = false;

        boolean outputDone = false;
        boolean inputDone = false;
        boolean decoderDone = false;

        while ( !outputDone ) {
            if ( VERBOSE )
                Log.d( TAG, "edit loop" );
            if ( !inputDone ) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USE);
                if ( inputBufIndex >= 0 ) {
                    if ( extractor.getSampleTime() / 1000 >= endTime ) {
                        decoder.queueInputBuffer( inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM );
                        inputDone = true;
                        if ( VERBOSE )
                            Log.d( TAG, "sent input EOS (with zero-length frame)" );
                    } else {
                        ByteBuffer inputBuf = decoder.getInputBuffer(inputBufIndex);
                        inputBuf.clear();

                        int sampleSize = extractor.readSampleData( inputBuf, 0 );
                        if ( sampleSize < 0 ) {
                            if ( VERBOSE )
                                Log.d( TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM" );
                            decoder.queueInputBuffer( inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM );
                        } else {
                            if ( VERBOSE )
                                Log.d( TAG, "InputBuffer ADVANCING" );
                            decoder.queueInputBuffer( inputBufIndex, 0, sampleSize, extractor.getSampleTime(), 0 );
                            extractor.advance();
                        }
                        inputChunk++;
                    }
                } else {
                    if ( VERBOSE )
                        Log.d( TAG, "input buffer not available" );
                }
            }

            boolean decoderOutputAvailable = !decoderDone;
            boolean encoderOutputAvailable = true;
            while ( decoderOutputAvailable || encoderOutputAvailable ) {
                int encoderStatus = mEncoder.dequeueOutputBuffer( info, TIMEOUT_USE);
                if ( encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER ) {
                    if ( VERBOSE )
                        Log.d( TAG, "no output from encoder available" );
                    encoderOutputAvailable = false;
                } else if ( encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED ) {
                    if ( VERBOSE )
                        Log.d( TAG, "encoder output buffers changed" );
                } else if ( encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ) {

                    MediaFormat newFormat = mEncoder.getOutputFormat();

                    Log.d(TAG+"test", "resampleVideo - output: " + newFormat);
                    mTrackIndex = mMuxer.addTrack( newFormat );

                    mMuxer.setOrientationHint(mRotation);
                    mMuxer.start();
                    if ( VERBOSE )
                        Log.d( TAG, "encoder output format changed: " + newFormat );
                } else if ( encoderStatus < 0 ) {
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = mEncoder.getOutputBuffer(encoderStatus);
                    if ( encodedData == null ) {
                    }
                    if ( info.size != 0 ) {
                        encodedData.position( info.offset );
                        encodedData.limit( info.offset + info.size );
                        outputCount++;

                        mMuxer.writeSampleData( mTrackIndex, encodedData, info );

                        if ( VERBOSE )
                            Log.d( TAG, "encoder output " + info.size + " bytes" );
                    }
                    outputDone = ( info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM ) != 0;

                    mEncoder.releaseOutputBuffer( encoderStatus, false );
                }

                if ( outputDoneNextTimeWeCheck ) {
                    outputDone = true;
                }

                if ( encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER ) {
                    continue;
                }
                if ( !decoderDone ) {
                    int decoderStatus = decoder.dequeueOutputBuffer( info, TIMEOUT_USE);
                    if ( decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER ) {
                        if ( VERBOSE )
                            Log.d( TAG, "no output from decoder available" );
                        decoderOutputAvailable = false;
                    } else if ( decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED ) {
                        if ( VERBOSE )
                            Log.d( TAG, "decoder output buffers changed (we don't care)" );
                    } else if ( decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ) {
                        MediaFormat newFormat = decoder.getOutputFormat();
                        Log.d(TAG+"test", "resampleVideo - input: " + newFormat);
                        if ( VERBOSE )
                            Log.d( TAG, "decoder output format changed: " + newFormat );
                    } else if ( decoderStatus < 0 ) {
                    } else { // decoderStatus >= 0
                        if ( VERBOSE )
                            Log.d( TAG, "surface decoder given buffer " + decoderStatus + " (size=" + info.size + ")" );
                        boolean doRender = ( info.size != 0 );

                        decoder.releaseOutputBuffer( decoderStatus, doRender );
                        if ( doRender ) {
                            if ( VERBOSE )
                                Log.d( TAG, "awaiting frame" );
                            mOutputSurface.awaitNewImage();
                            mOutputSurface.drawImage();

                            long nSecs = info.presentationTimeUs * 1000;

                            if ( video.getStartTime() != -1 ) {
                                nSecs = ( info.presentationTimeUs - ( video.getStartTime() * 1000 ) ) * 1000;
                            }

                            Log.d( TAG, "Setting presentation time " + nSecs / ( 1000 * 1000 ) );
                            nSecs = Math.max( 0, nSecs );

                            mEncoderPresentationTimeUs += ( nSecs - mLastSampleTime );

                            mLastSampleTime = nSecs;

                            mInputSurface.setPresentationTime( mEncoderPresentationTimeUs );
                            if ( VERBOSE )
                                Log.d( TAG, "swapBuffers" );
                            mInputSurface.swapBuffers();
                        }
                        if ( ( info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM ) != 0 ) {
                            outputDoneNextTimeWeCheck = true;
                        }
                    }
                }
            }
        }
        if ( inputChunk != outputCount ) {
            // throw new RuntimeException( "frame lost: " + inputChunk + " in, " + outputCount + " out" );
        }

    }

    private void muxAudioAndVideo(){

        try {

            //Video Extractor
            MediaExtractor videoExtractor = setupExtractorForVideo(new InputVideo(mOutputTempUri));

            int trackIndexVideo = getVideoTrackIndex(videoExtractor);

            videoExtractor.selectTrack( trackIndexVideo );

            MediaFormat videoFormat = videoExtractor.getTrackFormat( trackIndexVideo );
            Log.d(TAG+"test", "muxAudioAndVideo: " + videoFormat);


            //Audio Extractor
            MediaExtractor audioExtractor = setupExtractorForVideo(mInput);

            int trackIndexAudio = getAudioTrackIndex(audioExtractor);

            audioExtractor.selectTrack( trackIndexAudio );

            MediaFormat audioFormat = audioExtractor.getTrackFormat( trackIndexAudio );

            //Muxer
            MediaMuxer muxer = new MediaMuxer( mOutputRealUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 );
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

            while (!sawEOS)
            {
                videoBufferInfo.offset = offset;
                videoBufferInfo.size = videoExtractor.readSampleData(videoBuf, offset);


                if (videoBufferInfo.size < 0 || audioBufferInfo.size < 0)
                {
                    Log.d(TAG, "saw input EOS.");
                    sawEOS = true;
                    videoBufferInfo.size = 0;

                }
                else
                {
                    videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                    videoBufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    muxer.writeSampleData(videoIndex, videoBuf, videoBufferInfo);
                    videoExtractor.advance();

                }
            }


            boolean sawEOS2 = false;
            while (!sawEOS2)
            {

                audioBufferInfo.offset = offset;
                audioBufferInfo.size = audioExtractor.readSampleData(audioBuf, offset);

                if (videoBufferInfo.size < 0 || audioBufferInfo.size < 0)
                {
                    sawEOS2 = true;
                    audioBufferInfo.size = 0;
                }
                else
                {
                    audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                    audioBufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    muxer.writeSampleData(audioIndex, audioBuf, audioBufferInfo);
                    audioExtractor.advance();
                }
            }

            File temp = new File(mOutputTempUri.getPath());
            if(temp.exists())
                temp.delete();

            muxer.stop();
            muxer.release();

        }catch (Exception e){
            Log.e("TestMuxAudio", "Problem: " + e);
        }


    }

    private void releaseOutputResources() {

        if ( mInputSurface != null ) {
            mInputSurface.release();
        }

        if ( mEncoder != null ) {
            mEncoder.stop();
            mEncoder.release();
        }

        if ( mMuxer != null ) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }

    }

    private MediaExtractor setupExtractorForVideo(InputVideo video ) {

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource( video.getUri().toString() );
        } catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }

        return extractor;
    }

    private int getVideoTrackIndex(MediaExtractor extractor) {
        int index = -1;
        for ( int trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++ ) {
            MediaFormat format = extractor.getTrackFormat( trackIndex );

            String mime = format.getString( MediaFormat.KEY_MIME );
            if ( mime != null ) {
                if ( mime.startsWith( "video/" ) ) {
                    index =  trackIndex;
                }
            }
        }
        return index;
    }

    private int getAudioTrackIndex(MediaExtractor extractor) {

        for ( int trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++ ) {
            MediaFormat format = extractor.getTrackFormat( trackIndex );

            String mime = format.getString( MediaFormat.KEY_MIME );
            if ( mime != null ) {
                if ( mime.startsWith( "audio/" ) ) {
                    return trackIndex;
                }
            }
        }
        return -1;
    }

}