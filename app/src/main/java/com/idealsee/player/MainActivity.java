package com.idealsee.player;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import com.idealsee.rtsp.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.LinkedBlockingDeque;

public class MainActivity extends AppCompatActivity {

    private ExecutorService mThreadPool;
    private RtspEvent rtspEvent;
    private SurfaceView mPlaybackView;
    private SurfaceHolder mSurfaceHolder;
    /**
     * 解码
     */
    private LinkedBlockingDeque<byte[]> bufferQueue = new LinkedBlockingDeque<byte[]>();
    private MediaCodec mMeidaCodec;
    private RtspClient rtspClient;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private boolean isStartDecode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        mThreadPool = Executors.newCachedThreadPool();
        rtspEvent = new RtspEvent() {
            @Override
            public void onConnectionSuccessRtsp() {
                ConfigMediaCodec();
                mThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        int mCount = 0;
                        int inputBufferIndex, outputBufferIndex;
                        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                        boolean startKeyFrame = false;
                        while (true) {
                            try {
                                byte[] nalu = bufferQueue.take();
                                if (nalu == null) {
                                    continue;
                                }
                                Integer naluType = nalu[4] & 0x1F;
                                if (naluType == 5) startKeyFrame = true;
                                if (startKeyFrame || naluType == 7 || naluType == 8 || naluType == 1 || naluType == 6) {
                                    inputBufferIndex = mMeidaCodec.dequeueInputBuffer(10000);
                                    if (inputBufferIndex >= 0) {
                                        ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                                        inputBuffer.clear();
//                                        if (pBuf.length > buffer.remaining()) {
//                                            mCodec.queueInputBuffer(index, 0, 0, frameInfo.stamp, 0);
//                                        } else {
//                                            buffer.put(nalu, frameInfo.offset, frameInfo.length);
//                                            mCodec.queueInputBuffer(index, 0, buffer.position(), frameInfo.stamp + differ, 0);
//                                        }
                                        inputBuffer.put(nalu, 0, nalu.length);
                                        mMeidaCodec.queueInputBuffer(inputBufferIndex, 0, nalu.length, 1000000 * mCount / 20, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                                        outputBufferIndex = mMeidaCodec.dequeueOutputBuffer(info, 10000);
                                        switch (outputBufferIndex) {
                                            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                                                Log.d("myapp", "INFO_OUTPUT_BUFFERS_CHANGED");
                                                break;
                                            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                                                Log.d("myapp", "INFO_OUTPUT_FORMAT_CHANGED");
                                                break;
                                            case MediaCodec.INFO_TRY_AGAIN_LATER:
                                                Log.d("myapp", "INFO_TRY_AGAIN_LATER");
                                                break;
                                            default:
                                                mMeidaCodec.releaseOutputBuffer(outputBufferIndex, true);
                                                break;
                                        }
                                        mCount++;
                                    } else {
                                        continue;
                                    }
                                }

                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }

            @Override
            public void onConnectionFailedRtsp(String reason) {

            }

            @Override
            public void onDisconnectRtsp() {

            }

            @Override
            public void onAuthErrorRtsp() {

            }

            @Override
            public void onAuthSuccessRtsp() {

            }

            @Override
            public void onReceiveNALUPackage(byte[] nalu, int naluSize, int timestamp) {
                try {
                    bufferQueue.put(nalu);
                } catch (InterruptedException e) {
                    System.out.println("The buffer queue is full , wait for the place..");
                }
            }
        };
        mPlaybackView = (SurfaceView) findViewById(R.id.PlaybackView);
        mSurfaceHolder = mPlaybackView.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                System.out.println("创建好了");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            mThreadPool.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        rtspClient = new RtspClient("rtsp://192.168.0.64:554/h264/ch1/sub/av_stream", "admin", "abcd1234", rtspEvent);
//                                         rtspClient = new RtspClient("rtsp://172.16.10.237/h264/test", "", "", rtspEvent);
                                        rtspClient.Connect();
                                    }
                                });
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void ConfigMediaCodec() {
        try{
            MediaFormat mediaFormat = new MediaFormat();
            mediaFormat.setString(MediaFormat.KEY_MIME, "video/avc");
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,  rtspClient.picWidth*rtspClient.picHeight);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
            // mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(rtspClient.sps));
            mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(rtspClient.pps));
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, rtspClient.picWidth);
            mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, rtspClient.picHeight);
            mMeidaCodec = MediaCodec.createDecoderByType("video/avc");
            mMeidaCodec.configure(mediaFormat, mPlaybackView.getHolder().getSurface(), null, 0);
            mMeidaCodec.start();
            inputBuffers = mMeidaCodec.getInputBuffers();
            outputBuffers = mMeidaCodec.getOutputBuffers();
        } catch (IOException e) {
            System.out.println("MediaCodec faild!");
        }
    }
}
