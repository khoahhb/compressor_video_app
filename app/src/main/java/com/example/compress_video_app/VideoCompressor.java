package com.example.compress_video_app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


public class VideoCompressor {

    private static final String TAG = "VideoCompressor";

    private final File dir;
    private static final int TIMEOUT_USEC = 0;

    public static final int WIDTH_QCIF = 176;
    public static final int HEIGHT_QCIF = 144;
    public static final int BITRATE_QCIF = 1000000;

    public static final int WIDTH_QVGA = 320;
    public static final int HEIGHT_QVGA = 240;
    public static final int BITRATE_QVGA = 2000000;

    public static final int WIDTH_720P = 1280;
    public static final int HEIGHT_720P = 720;
    public static final int BITRATE_720P = 6000000;

    private static final boolean WORK_AROUND_BUGS = false; // avoid fatal codec bugs
    private static final boolean VERBOSE = false; // lots of logging

    public static final int FPS_30 = 30; // 30fps
    public static final int FPS_15 = 15; // 15fps
    public static final int IFRAME_INTERVAL_10 = 10;

    private int mWidth = WIDTH_720P;
    private int mHeight = HEIGHT_720P;
    private int mBitRate = BITRATE_720P;
    private int mFrameRate = FPS_30;
    private int mIFrameInterval = IFRAME_INTERVAL_10;

    List<InputVideo> mClips = new ArrayList<>();
    private final Uri mOutputTempUri;

    private Uri mOutputRealUri;

    InputSurface mInputSurface;
    OutputSurface mOutputSurface;
    MediaCodec mEncoder = null;
    MediaMuxer mMuxer = null;
    int mTrackIndex = -1;
    boolean mMuxerStarted = false;
    long mLastSampleTime = 0;
    long mEncoderPresentationTimeUs = 0;

    public VideoCompressor(Context context) throws IOException {
        dir = new File(context.getFilesDir(), "temp_videos");
        if (!dir.exists()) {
            dir.mkdir();
        }
        File outputTempFile = new File(dir, "outputTemp.mp4");
        if (!outputTempFile.exists()) {
            outputTempFile.createNewFile();
        }
        mOutputTempUri = Uri.fromFile(outputTempFile);
    }

    public void addVideo(InputVideo clip )
    {
        mClips.add( clip );

        MediaExtractor extractor = setupExtractorForClip(clip);

        if(extractor == null ) {
            return;
        }

        int trackIndex = getVideoTrackIndex(extractor);
        extractor.selectTrack( trackIndex );

        MediaFormat clipFormat = extractor.getTrackFormat( trackIndex );

        mHeight = clipFormat.getInteger(MediaFormat.KEY_HEIGHT);
        mWidth = clipFormat.getInteger(MediaFormat.KEY_WIDTH);
    }

    public void setOutput( String realName ) throws IOException {
        File outputRealFile = new File(dir, realName);
        if (!outputRealFile.exists()) {
            outputRealFile.createNewFile();
        }
        mOutputRealUri = Uri.fromFile(outputRealFile);
    }

    public void setOutputResolution( int width, int height ) {
        if ( ( width % 16 ) != 0 || ( height % 16 ) != 0 ) {
            Log.w( TAG, "WARNING: width or height not multiple of 16" );
        }
        mWidth = width;
        mHeight = height;
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
                resampleVideo();

                muxAudioAndVideo();

            } catch ( Throwable th ) {
                mThrowable = th;
            }
        }

    }

    private void setupEncoder() {
        try {
            MediaFormat outputFormat = MediaFormat.createVideoFormat( MediaHelper.MIME_TYPE_AVC, mWidth, mHeight );
            outputFormat.setInteger( MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface );
            outputFormat.setInteger( MediaFormat.KEY_BIT_RATE, mBitRate );
            outputFormat.setInteger( MediaFormat.KEY_FRAME_RATE, mFrameRate );
            outputFormat.setInteger( MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval );
            outputFormat.setInteger( MediaFormat.KEY_MAX_WIDTH, mWidth );
            outputFormat.setInteger( MediaFormat.KEY_MAX_HEIGHT, mHeight );

            mEncoder = MediaCodec.createEncoderByType( MediaHelper.MIME_TYPE_AVC );

            mEncoder.configure( outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE );
            mInputSurface = new InputSurface( mEncoder.createInputSurface() );
            mInputSurface.makeCurrent();
            mEncoder.start();
        } catch (Exception e) {
            Log.e(TAG, "setupEncoder: " + e);
            e.printStackTrace();
        }
    }

    private void setupMuxer() {

        try {
            mMuxer = new MediaMuxer( mOutputTempUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 );
        } catch ( IOException ioe ) {
            throw new RuntimeException( "MediaMuxer creation failed", ioe );
        }
    }

    private void resampleVideo() {

        setupMuxer();
        setupEncoder();

        for ( InputVideo clip : mClips ) {
            feedClipToEncoder( clip );
        }

        mEncoder.signalEndOfInputStream();

        releaseOutputResources();
    }

    private void feedClipToEncoder( InputVideo clip ) {

        mLastSampleTime = 0;

        MediaCodec decoder = null;

        MediaExtractor extractor = setupExtractorForClip(clip);

        if(extractor == null ) {
            return;
        }

        int trackIndex = getVideoTrackIndex(extractor);
        extractor.selectTrack( trackIndex );

        MediaFormat clipFormat = extractor.getTrackFormat( trackIndex );

        if ( clip.getStartTime() != -1 ) {
            extractor.seekTo( clip.getStartTime() * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC );
            clip.setStartTime( extractor.getSampleTime() / 1000 );
        }

        try {
            decoder = MediaCodec.createDecoderByType( MediaHelper.MIME_TYPE_AVC );
            mOutputSurface = new OutputSurface();

            decoder.configure( clipFormat, mOutputSurface.getSurface(), null, 0 );
            decoder.start();

            resampleVideo( extractor, decoder, clip );


        } catch (IOException e) {
            Log.e(TAG, "feedClipToEncoder: " + e);
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
                extractor = null;
            }
        }
    }

    private MediaExtractor setupExtractorForClip(InputVideo clip ) {

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource( clip.getUri().toString() );
        } catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }

        return extractor;
    }

    private void resampleVideo( MediaExtractor extractor, MediaCodec decoder, InputVideo clip ) {
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int outputCount = 0;

        long endTime = clip.getEndTime();

        if ( endTime == -1 ) {
            endTime = clip.getVideoDuration();
        }

        boolean outputDoneNextTimeWeCheck = false;

        boolean outputDone = false;
        boolean inputDone = false;
        boolean decoderDone = false;

        while ( !outputDone ) {
            if ( VERBOSE )
                Log.d( TAG, "edit loop" );
            if ( !inputDone ) {
                int inputBufIndex = decoder.dequeueInputBuffer( TIMEOUT_USEC );
                if ( inputBufIndex >= 0 ) {
                    if ( extractor.getSampleTime() / 1000 >= endTime ) {
                        decoder.queueInputBuffer( inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM );
                        inputDone = true;
                        if ( VERBOSE )
                            Log.d( TAG, "sent input EOS (with zero-length frame)" );
                    } else {
                        ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                        inputBuf.clear();

                        int sampleSize = extractor.readSampleData( inputBuf, 0 );
                        if ( sampleSize < 0 ) {
                            Log.d( TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM" );
                            decoder.queueInputBuffer( inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM );
                        } else {
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
                int encoderStatus = mEncoder.dequeueOutputBuffer( info, TIMEOUT_USEC );
                if ( encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER ) {
                    if ( VERBOSE )
                        Log.d( TAG, "no output from encoder available" );
                    encoderOutputAvailable = false;
                } else if ( encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED ) {
                    encoderOutputBuffers = mEncoder.getOutputBuffers();
                    if ( VERBOSE )
                        Log.d( TAG, "encoder output buffers changed" );
                } else if ( encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ) {

                    MediaFormat newFormat = mEncoder.getOutputFormat();

                    mTrackIndex = mMuxer.addTrack( newFormat );
                    mMuxer.start();
                    mMuxerStarted = true;
                    if ( VERBOSE )
                        Log.d( TAG, "encoder output format changed: " + newFormat );
                } else if ( encoderStatus < 0 ) {
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
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
                    int decoderStatus = decoder.dequeueOutputBuffer( info, TIMEOUT_USEC );
                    if ( decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER ) {
                        if ( VERBOSE )
                            Log.d( TAG, "no output from decoder available" );
                        decoderOutputAvailable = false;
                    } else if ( decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED ) {
                        if ( VERBOSE )
                            Log.d( TAG, "decoder output buffers changed (we don't care)" );
                    } else if ( decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ) {
                        MediaFormat newFormat = decoder.getOutputFormat();
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

                            if ( clip.getStartTime() != -1 ) {
                                nSecs = ( info.presentationTimeUs - ( clip.getStartTime() * 1000 ) ) * 1000;
                            }

                            Log.d( "this", "Setting presentation time " + nSecs / ( 1000 * 1000 ) );
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

    @SuppressLint("WrongConstant")
    private void muxAudioAndVideo(){

        try {

            //Video Extractor
            MediaExtractor videoExtractor = setupExtractorForClip(new InputVideo(mOutputTempUri));

            int trackIndexVideo = getVideoTrackIndex(videoExtractor);

            videoExtractor.selectTrack( trackIndexVideo );

            MediaFormat videoFormat = videoExtractor.getTrackFormat( trackIndexVideo );

            //Audio Extractor
            MediaExtractor audioExtractor = setupExtractorForClip(mClips.get(0));

            int trackIndexAudio = getAudioTrackIndex(audioExtractor);

            audioExtractor.selectTrack( trackIndexAudio );

            MediaFormat audioFormat = audioExtractor.getTrackFormat( trackIndexAudio );

            //Muxer
            MediaMuxer muxer = new MediaMuxer( mOutputRealUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 );
            int videoIndex = muxer.addTrack(videoFormat);
            int audioIndex = muxer.addTrack(audioFormat);

            boolean sawEOS = false;
            int frameCount = 0;
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
                    videoBufferInfo.flags = videoExtractor.getSampleFlags();
                    muxer.writeSampleData(videoIndex, videoBuf, videoBufferInfo);
                    videoExtractor.advance();


                    frameCount++;

                }
            }


            boolean sawEOS2 = false;
            int frameCount2 =0;
            while (!sawEOS2)
            {
                frameCount2++;

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
                    audioBufferInfo.flags = audioExtractor.getSampleFlags();
                    muxer.writeSampleData(audioIndex, audioBuf, audioBufferInfo);
                    audioExtractor.advance();
                }
            }


            muxer.stop();
            muxer.release();


        }catch (Exception e){
            Log.e("TestMuxAudio", "Problem: " + e.toString());
        }


    }

//    @SuppressLint("WrongConstant")
//    private void muxAudioAndVideo(){
//
//        try {
//
//            //Video Extractor
//            MediaExtractor videoExtractor = setupExtractorForClip(new InputVideo(mOutputTempUri));
//
//            int trackIndexVideo = getVideoTrackIndex(videoExtractor);
//
//            videoExtractor.selectTrack( trackIndexVideo );
//
//            MediaFormat videoFormat = videoExtractor.getTrackFormat( trackIndexVideo );
//
//            //Audio Extractor
//            MediaExtractor audioExtractor = setupExtractorForClip(mClips.get(0));
//
//            int trackIndexAudio = getAudioTrackIndex(audioExtractor);
//
//            audioExtractor.selectTrack( trackIndexAudio );
//
//            MediaFormat audioFormat = audioExtractor.getTrackFormat( trackIndexAudio );
//
//            //Muxer
//            MediaMuxer muxer = new MediaMuxer( mOutputRealUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 );
//            int videoIndex = muxer.addTrack(videoFormat);
//            int audioIndex = muxer.addTrack(audioFormat);
//            muxer.start();
//
//            int maxChunkSize = 1024 * 1024;
//            ByteBuffer buffer = ByteBuffer.allocate(maxChunkSize);
//            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//
//            while (true) {
//                int chunkSize = videoExtractor.readSampleData(buffer, 0);
//
//                if (chunkSize > 0) {
//                    bufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
//                    bufferInfo.flags = videoExtractor.getSampleFlags();
//                    bufferInfo.size = chunkSize;
//
//                    muxer.writeSampleData(videoIndex, buffer, bufferInfo);
//
//                    videoExtractor.advance();
//
//                } else {
//                    break;
//                }
//            }
//
////            while (true) {
////                int chunkSize = audioExtractor.readSampleData(buffer, 0);
////
////                if (chunkSize >= 0) {
////                    bufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
////                    bufferInfo.flags = audioExtractor.getSampleFlags();
////                    bufferInfo.size = chunkSize;
////
////                    muxer.writeSampleData(audioIndex, buffer, bufferInfo);
////                    audioExtractor.advance();
////                } else {
////                    break;
////                }
////            }
//        }catch (Exception e){
//            Log.e("TestMuxAudio", "Problem: " + e.toString());
//        }
//
//
//    }

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

}