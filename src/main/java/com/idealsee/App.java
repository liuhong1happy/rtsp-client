package com.idealsee;

import com.idealsee.rtsp.RtspClient;

//rtsp://127.0.0.1:5050/ok

/**
 * Hello world!
 *
 */
public class App
{
    public static RtspClient rtspClient;
    public static void main( String[] args )
    {
        rtspClient = new RtspClient("rtsp://10.0.0.242/h264/test", new ApplicationRtspEvent());
        rtspClient.Connect();
    }
}
