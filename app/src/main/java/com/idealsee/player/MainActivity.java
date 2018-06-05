package com.idealsee.player;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import com.idealsee.rtsp.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ExecutorService mThreadPool;
    private RtspEvent rtspEvent;
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
        rtspEvent = new RtspEvent(){
            @Override
            public void onConnectionSuccessRtsp() {

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
            public void onReceiveNALUPackage(byte[] nalu, int naluSize) {

            }
        };
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
                                        RtspClient rtspClient = new RtspClient("rtsp://192.168.0.100:554/h264/ch1/main/av_stream", "admin", "abcd1234", rtspEvent);
                                        rtspClient.Connect();
                                    }
                                });
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
