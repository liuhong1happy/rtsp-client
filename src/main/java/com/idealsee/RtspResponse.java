package com.idealsee;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;

public class RtspResponse {

    /**协议固定信息 */
    private String Tag = "RTSP";
    private String Version = "1.0";
    /** RTSP协议部分 */
    public Integer Status = 200;
    public HashMap<String, String> Headers;
    public HashMap<String, String> BodyMap;
    public String Body;

    public RtspResponse(BufferedReader reader) {
        // no action
        try{
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException | NullPointerException e) {
            //TODO: handle exception
        }
    }
}