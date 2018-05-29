package com.idealsee;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RtspResponse {

    /**协议固定信息 */
    public String Tag = "RTSP";
    public String Version = "1.0";
    /** RTSP协议部分 */
    public Integer Status = 200;
    public String StatusMsg = "";
    public String SessionId = "";
    public HashMap<String, String> Headers;
    public RtspBodyMap<String, String> BodyMap;
    public String Body;

    public RtspResponse(BufferedReader reader) {
        Headers = new HashMap<String, String>();
        try{
            String line;
            while ((line = reader.readLine()) != null) {
                Pattern firstPattern = Pattern.compile("(\\S+)\\/(\\S+)\\s(\\d+)\\s(.+)");
                Matcher firsMatcher = firstPattern.matcher(line);
                if(firsMatcher.find()){
                    Tag = firsMatcher.group(1);
                    Version = firsMatcher.group(2);
                    Status = Integer.valueOf(firsMatcher.group(3));
                    StatusMsg = firsMatcher.group(4);
                }

                Pattern headerPattern = Pattern.compile("(\\S+)\\:\\s(.+)");
                Matcher headerMatcher = headerPattern.matcher(line);
                if (headerMatcher.find()) {
                    String key = headerMatcher.group(1);
                    String value = headerMatcher.group(2);
                    Headers.put(key, value);
                }
                if(Headers.containsKey("Session")) {
                    SessionId = Headers.get("Session");
                }
                System.out.println(line);
            }
        } catch (IOException | NullPointerException e) {
            //TODO: handle exception
        }
    }
}