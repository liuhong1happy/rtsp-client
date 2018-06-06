package com.idealsee.rtsp;

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
    public String Digest;
    public String Basic;
    public Integer ContentLength = 0;
    public SDPInfo Sdp;
    /**
     * 正则提取
     */
    public static final Pattern regexSDPgetTrack = Pattern.compile("control:(\\S+)",Pattern.CASE_INSENSITIVE);
    public static final Pattern regexSDPgetTrackValue = Pattern.compile("=(\\d+)",Pattern.CASE_INSENSITIVE);
    public static final Pattern regexSDPpacketizationMode = Pattern.compile("packetization-mode=(\\d);",Pattern.CASE_INSENSITIVE);
    public static final Pattern regexSDPspspps = Pattern.compile("sprop-parameter-sets=(\\S+),(\\S+)",Pattern.CASE_INSENSITIVE);

    public class SDPInfo {
        public boolean audioTrackFlag;
        public boolean videoTrackFlag;
        public String videoTrack;
        public String audioTrack;
        public Integer videoTrackValue;
        public Integer audioTrackValue;
        public String SPS;
        public String PPS;
        public int packetizationMode;
    }

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
                    continue;
                }
                
                Pattern authenticatePattern = Pattern.compile("WWW-Authenticate\\:\\s(Digest|Basic)\\s([\\S\\s]+)");
                Matcher authenticateMatcher = authenticatePattern.matcher(line);
                if(authenticateMatcher.find()){
                    String key = authenticateMatcher.group(1);
                    String value = authenticateMatcher.group(2);
                    if(key.equals("Digest")) {
                        Digest = value;
                    } else {
                        Basic = value;
                    }
                    continue;
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
                if(Headers.containsKey("Content-Length")) {
                    ContentLength = Integer.valueOf(Headers.get("Content-Length"));
                }
                System.out.println(line);
                if (line.length() < 3) break;
            }

            if(ContentLength > 0) {
                BodyMap = new RtspBodyMap<String, String>();
                Body = "";
                String sdpType = "sdp"; // sdp video audio
                Sdp = new SDPInfo();
                while ((line = reader.readLine()) != null) {
                    Pattern bodyPattern = Pattern.compile("^([a-z])=([\\s\\S]+)");
                    Matcher bodyMatcher = bodyPattern.matcher(line);
                    if (bodyMatcher.find()) {
                        String key = bodyMatcher.group(1);
                        String value = bodyMatcher.group(2);
                        BodyMap.put(key, value);
                        Body += line+"\r\n";
                        if(key.equals("m")) {
                            sdpType = value.contains("video") ? "video" : "audio";
                        }

                        switch (sdpType) {
                            case "sdp":
                                break;
                            case "video":
                                if(key.equals("a")) {
                                    Matcher trackMather = regexSDPgetTrack.matcher(value);
                                    if(trackMather.find()) {
                                        Sdp.videoTrack = trackMather.group(1);
                                        Sdp.videoTrackFlag = true;

                                        Matcher trackValueMather = regexSDPgetTrackValue.matcher(value);
                                        if (trackValueMather.find()) {
                                            Sdp.videoTrackValue =Integer.valueOf(trackValueMather.group(1));
                                        }
                                    }

                                    Matcher spsMather = regexSDPspspps.matcher(value);
                                    if(spsMather.find()) {
                                        Sdp.SPS = spsMather.group(1);
                                        Sdp.PPS = spsMather.group(2);
                                    }
                                }
                                break;
                            case "audio":
                                if(key.equals("a")) {
                                    Matcher trackMather = regexSDPgetTrack.matcher(value);
                                    if (trackMather.find()) {
                                        Sdp.videoTrack = trackMather.group(1);
                                        Sdp.videoTrackFlag = true;
                                        Matcher trackValueMather = regexSDPgetTrackValue.matcher(value);
                                        if (trackValueMather.find()) {
                                            Sdp.videoTrackValue =Integer.valueOf(trackValueMather.group(1));
                                        }
                                    }
                                }
                                break;
                        }
                    }
                    System.out.println(line);
                    if (line.length() < 3) break;
                }
            }
        } catch (IOException | NullPointerException e) {
            //TODO: handle exception
        }
    }
}