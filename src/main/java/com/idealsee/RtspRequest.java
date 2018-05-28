package com.idealsee;

import java.util.HashMap;
import java.util.Map.Entry;
import java.io.BufferedWriter;
import java.io.IOException;

public class RtspRequest {
    /**协议固定信息 */
    private String Tag = "RTSP";
    private String Version = "1.0";
    /** 请求体封装 */
    public String Method = "OPTIONS";
    public String Url = "rtsp://127.0.0.1:554/test";
    public HashMap<String, String> Headers;
    public HashMap<String, String> BodyMap;
    public String Body;

    public RtspRequest(String url, String method) {
        this.Url = url;
        this.Method = method;
        this.Headers = new HashMap<String, String>();
        this.BodyMap = new HashMap<String, String>();
        this.Body = null;
    }

    public String toString() {
        String request = "";
        request += this.Method + " "  + this.Url + " " + this.Tag + "/" + this.Version+"\r\n";
        for (Entry<String, String> entry : this.Headers.entrySet()) {
            request += entry.getKey() + ":" + entry.getValue() +"\r\n";
        }

        request += "\r\n\r\n";

        for (Entry<String, String> entry : this.BodyMap.entrySet()) {
            request += entry.getKey() + "=" + entry.getValue() +"\r\n";
        }
        
        if(this.Body != null) {
            request += this.Body;
        }
        return request;
    }

    public void sendByBufferedWriter(BufferedWriter writer) {
        try {
            writer.write(this.toString());
            writer.flush();
        } catch (IOException | NullPointerException e) {
            //TODO: handle exception
        }

    }
}