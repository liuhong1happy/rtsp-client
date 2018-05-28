package com.idealsee;

//rtsp://127.0.0.1:5050/ok

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        RtspClient rtspClient = new RtspClient("rtsp://10.0.0.242/test", new RtspEventImpl());
        rtspClient.Connect();
    }
}
