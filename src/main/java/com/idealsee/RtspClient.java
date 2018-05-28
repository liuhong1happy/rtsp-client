package com.idealsee;

import java.net.Socket;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;

public class RtspClient {
    public final String TAG = "RtspClient";
    public RtspEvent rtspEvent;
    /** RTSP协议相关 */
    public String url;
    public String host;
    public Integer port;
    public String path;
    public String authorization = null;
    public int mCSeq = 0;
    public String sessionId;
    public Boolean tlsEnabled = false;
    public Boolean streaming = false;
    private final long timestamp;
    private final int trackVideo = 1;
    private final int trackAudio = 0;
    private Protocol protocol = Protocol.TCP;
    private String defaultSPS = "Z0KAHtoHgUZA";
    private String defaultPPS = "aM4NiA==";
    private byte[] sps, pps;
    
    /** Socket通信相关 */
    public Socket socket;
    public BufferedReader reader;
    public BufferedWriter writer;

    private static final Pattern rtspUrlPattern = Pattern.compile("^rtsp://([^/:]+)(:(\\d+))*/([^/]+)(/(.*))*$");
    private static final Pattern rtspsUrlPattern = Pattern.compile("^rtsps://([^/:]+)(:(\\d+))*/([^/]+)(/(.*))*$");

    public RtspClient(String url, RtspEvent rtspEvent) {
        this.rtspEvent = rtspEvent;
        this.url = url;
        this.setUrl(url);
        long uptime = System.currentTimeMillis();
        timestamp = (uptime / 1000) << 32 & (((uptime - ((uptime / 1000) * 1000)) >> 32)/ 1000);
    }

    public RtspClient(String url, String authorization, RtspEvent rtspEvent) {
        this.rtspEvent = rtspEvent;
        this.url = url;
        this.authorization = authorization;
        this.setUrl(url);
        long uptime = System.currentTimeMillis();
        timestamp = (uptime / 1000) << 32 & (((uptime - ((uptime / 1000) * 1000)) >> 32)/ 1000);
    }
    /**
     * 设置RTSP url
     */
    public void setUrl(String url) {
        Matcher rtspMatcher = rtspUrlPattern.matcher(url);
        Matcher rtspsMatcher = rtspsUrlPattern.matcher(url);
        Matcher matcher;
        if (rtspMatcher.matches()) {
          matcher = rtspMatcher;
          tlsEnabled = false;
        } else if (rtspsMatcher.matches()) {
          matcher = rtspsMatcher;
          tlsEnabled = true;
        } else {
          streaming = false;
          rtspEvent.onConnectionFailedRtsp("Endpoint malformed, should be: rtsp://ip:port/appname/streamname");
          return;
        }
        host = matcher.group(1);
        port = Integer.parseInt((matcher.group(3) != null) ? matcher.group(3) : "554");
        path = "/" + matcher.group(4) + "/" + matcher.group(6);
    }

    /**
     * 链接RTSP服务器
     * 1. 发送OPTIONS请求
     * 2. 发送DESCRIBE请求
     */
    public void Connect() {
        try {
            // 1. 创建socket
            SocketAddress socketAddress = new InetSocketAddress(host, port);
            socket = new Socket();
            socket.connect(socketAddress, 3000);
            socket.setSoTimeout(3000);

            // 2. 获取读取流
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // 3. 发送OPTIONS请求
            sendOptions();
            // 4. 发送ANNOUNCE请求
            sendAnnounce();
            


        } catch (IOException | NullPointerException e) {
            rtspEvent.onConnectionFailedRtsp(e.toString());
        }
    }

    /**
     * 发送Options请求
     */
    public RtspResponse sendOptions() {
        RtspRequest optionsRequest = new RtspRequest("rtsp://" + host +":"+ port + "" + path, "OPTIONS");
        optionsRequest.Headers.put("CSeq", String.valueOf(++mCSeq));
        optionsRequest.Headers.put("Content-Length", String.valueOf(0));
        if(sessionId != null) {
             optionsRequest.Headers.put("Session",sessionId);
        }
        if(authorization != null) {
             optionsRequest.Headers.put("Authorization",authorization);
        }
        optionsRequest.sendByBufferedWriter(writer);
        return new RtspResponse(reader);
    }

    public void sendAnnounce() {
        RtspRequest optionsRequest = new RtspRequest("rtsp://" + host +":"+ port + "" + path, "ANNOUNCE");
         optionsRequest.Headers.put("CSeq",String.valueOf(++mCSeq));
         optionsRequest.Headers.put("Content-Length",String.valueOf(0));
         optionsRequest.Headers.put("Content-Type", "application/sdp");
        if(sessionId != null) {
             optionsRequest.Headers.put("Session",sessionId);
        }
        if(authorization != null) {
             optionsRequest.Headers.put("Authorization",authorization);
        }
        optionsRequest.BodyMap.put("v", String.valueOf(0));
        optionsRequest.BodyMap.put("o", "-"+timestamp + " "+timestamp+" IN IP4 127.0.0.1");
        optionsRequest.BodyMap.put("s", "Unnamed");
        optionsRequest.BodyMap.put("i", "N/A");
        optionsRequest.BodyMap.put("c", "IN IP4 "+host);
        optionsRequest.BodyMap.put("t", "0 0");
        optionsRequest.BodyMap.put("a", "recvonly");

        optionsRequest.sendByBufferedWriter(writer);
    }

    public String getSPS() {
        String sSPS;
        if (sps != null ) {
            sSPS = Base64.getEncoder().encodeToString(sps);
        } else {
            sSPS = defaultSPS;
        }
        return sSPS;
    }

    public String getPPS() {
        String sPPS;
        if (pps != null ) {
            sPPS = Base64.getEncoder().encodeToString(pps);
        } else {
            sPPS = defaultPPS;
        }
        return sPPS;
    }
}