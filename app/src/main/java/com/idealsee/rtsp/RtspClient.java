package com.idealsee.rtsp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.idealsee.rtp.H264Package;
import com.idealsee.rtp.RtpEvent;
import com.idealsee.rtp.RtpPackage;
import com.idealsee.rtp.RtpSocket;
import com.idealsee.utils.MD5Util;

import android.util.Base64;

public class RtspClient implements RtpEvent {
    public final String TAG = "RtspClient";
    public RtspEvent rtspEvent;
    /** RTSP协议相关 */
    public String url;
    public String host;
    public Integer port;
    public String path;
    public String user;
    public String password;
    public String authorization = null;
    public int mCSeq = 0;
    public String sessionId;
    public Boolean tlsEnabled = false;
    public Boolean streaming = false;
    private final long timestamp;
    private final int trackVideo = 1;
    private final int trackAudio = 0;
    private Protocol protocol = Protocol.UDP;
    private String defaultSPS = "Z0KAHtoHgUZA";
    private String defaultPPS = "aM4NiA==";
    public byte[] sps, pps;
    private int sampleRate = 44100;
    private boolean isStereo = true;
    private Integer startPort = 15000;
    public String trackID = "streamid";
    public Integer picWidth = 1920;
    public Integer picHeight = 1080;
    private static final int[] AUDIO_SAMPLING_RATES = {
        96000, // 0
        88200, // 1
        64000, // 2
        48000, // 3
        44100, // 4
        32000, // 5
        24000, // 6
        22050, // 7
        16000, // 8
        12000, // 9
        11025, // 10
        8000,  // 11
        7350,  // 12
        -1,   // 13
        -1,   // 14
        -1,   // 15
    };
  
    
    /** Socket通信相关 */
    public Socket socket;
    public BufferedReader reader;
    public BufferedWriter writer;
    public RtpSocket aacRtpSocket;
    public RtpSocket h264RtpSocket;
    public RtspStatus status = RtspStatus.Init;

    private static final Pattern rtspUrlPattern = Pattern.compile("^rtsp://([^/:]+)(:(\\d+))*/([^/]+)(/(.*))*$");
    private static final Pattern rtspsUrlPattern = Pattern.compile("^rtsps://([^/:]+)(:(\\d+))*/([^/]+)(/(.*))*$");
    /**
     * 接受包缓存与转换
     */
    public HashMap<Integer, ArrayList<H264Package>> h264PackageCache;
    public final byte[] StartCode = { 0, 0, 0 , 1 };

    public RtspClient(String url, RtspEvent rtspEvent) {
        this.rtspEvent = rtspEvent;
        this.url = url;
        this.setUrl(url);
        long uptime = System.currentTimeMillis();
        timestamp = (uptime / 1000) << 32 & (((uptime - ((uptime / 1000) * 1000)) >> 32)/ 1000);
        this.h264PackageCache = new HashMap<Integer, ArrayList<H264Package>>();
    }

    public RtspClient(String url, String authorization, RtspEvent rtspEvent) {
        this.rtspEvent = rtspEvent;
        this.url = url;
        this.authorization = authorization;
        this.setUrl(url);
        long uptime = System.currentTimeMillis();
        timestamp = (uptime / 1000) << 32 & (((uptime - ((uptime / 1000) * 1000)) >> 32)/ 1000);
        this.h264PackageCache = new HashMap<Integer, ArrayList<H264Package>>();
    }

    public RtspClient(String url,String user, String pwd, RtspEvent rtspEvent) {
        this.rtspEvent = rtspEvent;
        this.url = url;
        this.setUrl(url);
        long uptime = System.currentTimeMillis();
        timestamp = (uptime / 1000) << 32 & (((uptime - ((uptime / 1000) * 1000)) >> 32)/ 1000);
        this.h264PackageCache = new HashMap<Integer, ArrayList<H264Package>>();
        this.user = user;
        this.password = pwd;
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
     * 2. 发送ANNOUNCE请求
     */
    public void Connect() {
        try {
            // 1. 创建socket
            SocketAddress socketAddress = new InetSocketAddress(host, port);
            socket = new Socket();
            socket.connect(socketAddress, 3000);
            socket.setSoTimeout(3000);
            aacRtpSocket = new RtpSocket(startPort, this);
            h264RtpSocket = new RtpSocket(startPort + 2, this);
            
            // 2. 获取读取流
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            // 3. 发送OPTIONS请求
            sendOptions();
            // 4. 发送DESCRIBE请求
            RtspResponse responseDescribe = sendDescribe();
            if(responseDescribe.Status == 401) {
                // 重新授权登录
                if(responseDescribe.Digest != null) {
                    this.authorization = this.createAuth(responseDescribe.Digest, "DESCRIBE");
                }
                responseDescribe = sendDescribe();
                if(responseDescribe.Status != 200) {
                    return;
                } else {
                    sps = Base64.decode(responseDescribe.Sdp.SPS, Base64.NO_WRAP);
                    pps = Base64.decode(responseDescribe.Sdp.PPS, Base64.NO_WRAP);
                    decodeSPS();
                }
            }

            // 5. 发送Setup请求
            if(responseDescribe.Sdp.videoTrackFlag) {
                RtspResponse responseSetup1 = sendSetup(responseDescribe.Sdp.videoTrack, responseDescribe.Sdp.videoTrackValue, protocol, "play");
            }
            if(responseDescribe.Sdp.audioTrackFlag) {
                RtspResponse responseSetup2 = sendSetup(responseDescribe.Sdp.audioTrack, responseDescribe.Sdp.audioTrackValue, protocol, "play");
            }

            Play();

            status = RtspStatus.Ready;
            rtspEvent.onConnectionSuccessRtsp();
            // sendPause();
            // sendTeardown();
        } catch (IOException | NullPointerException e) {
            rtspEvent.onConnectionFailedRtsp(e.toString());
        }
    }

    public boolean isReady() {
        return status == RtspStatus.Ready;
    }

    public void Play() {
        RtspResponse response = sendPlay();
        if(response.Status == 200) {
            h264RtpSocket.start();
            aacRtpSocket.start();

            status = RtspStatus.Playing;
        }
    }


    public void Pause() {
        RtspResponse response = sendPause();
        if(response.Status == 200) {
            h264RtpSocket.close();
            aacRtpSocket.close();
            status = RtspStatus.Ready;
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

    /**
     * 发送Announce请求
     */
    public RtspResponse sendAnnounce() {
        RtspRequest announceRequest = new RtspRequest("rtsp://" + host +":"+ port + "" + path, "ANNOUNCE");
         announceRequest.Headers.put("CSeq",String.valueOf(++mCSeq));
         announceRequest.Headers.put("Content-Length",String.valueOf(0));
         announceRequest.Headers.put("Content-Type", "application/sdp");
        if(sessionId != null) {
             announceRequest.Headers.put("Session",sessionId);
        }
        if(authorization != null) {
             announceRequest.Headers.put("Authorization",authorization);
        }
        announceRequest.BodyMap.put("v", String.valueOf(0));
        announceRequest.BodyMap.put("o", "- " + timestamp + " " + timestamp + " IN IP4 127.0.0.1");
        announceRequest.BodyMap.put("s", "Unnamed");
        announceRequest.BodyMap.put("i", "N/A");
        announceRequest.BodyMap.put("c", "IN IP4 " + host);
        announceRequest.BodyMap.put("t", "0 0");
        announceRequest.BodyMap.put("a", "recvonly");

        announceRequest.BodyMap.putAll(createAudioBody(trackAudio, sampleRate, isStereo));
        announceRequest.BodyMap.putAll(createVideoBody(trackVideo, getSPS(), getPPS()));

        announceRequest.sendByBufferedWriter(writer);

        return new RtspResponse(reader);
    }

    /**
     * 发送Describe请求
     ***************************************response区域的计算规则如下***********************************
        (1)当password为MD5编码,则
        response = md5(<password>:<nonce>:md5(<cmd>:<url>));
        (2)当password为ANSI字符串,则
        response = md5(md5(<username>:<realm>:<password>):<nonce>:md5(<cmd>:<uri>));
     ***********************************************************************************************************
     */
    public RtspResponse sendDescribe() {
        RtspRequest optionsRequest = new RtspRequest("rtsp://" + host +":"+ port + "" + path, "DESCRIBE");
        optionsRequest.Headers.put("CSeq", String.valueOf(++mCSeq));
        if(sessionId != null) {
             optionsRequest.Headers.put("Session",sessionId);
        }
        if(authorization != null) {
             optionsRequest.Headers.put("Authorization",authorization);
        }
        optionsRequest.sendByBufferedWriter(writer);
        return new RtspResponse(reader);
    }

    /**
     * 发送Setup请求
     */
    public RtspResponse sendSetup(String track, int trackType, Protocol protocol, String mode) {
        String params = (protocol == Protocol.UDP) ? ("UDP;unicast;client_port=" + (startPort + 2 * trackType) + "-" + (startPort + 2 * trackType + 1) + ";mode="+mode)
										: ("TCP;interleaved=" + 2 * trackType + "-" + (2 * trackType + 1) + ";mode="+mode);
										
		RtspRequest setupRequest = new RtspRequest(track, "SETUP");
		setupRequest.Headers.put("CSeq",String.valueOf(++mCSeq));
        setupRequest.Headers.put("Transport", "RTP/AVP/"+params);
        if(sessionId != null) {
            setupRequest.Headers.put("Session",sessionId);
       }
       if(authorization != null) {
            setupRequest.Headers.put("Authorization",authorization.replace("DESCRIBE","SETUP"));
       }
       setupRequest.sendByBufferedWriter(writer);
       return new RtspResponse(reader);
    }

    /**
     * 发送Record请求
     */
    public RtspResponse sendRecord() {
			RtspRequest recordRequest = new RtspRequest("rtsp://" + host +":"+ port + "" + path, "RECORD");
			recordRequest.Headers.put("CSeq",String.valueOf(++mCSeq));
			recordRequest.Headers.put("Range", "npt=0.000-");

			if(sessionId != null) {
					recordRequest.Headers.put("Session",sessionId);
			}
			if(authorization != null) {
					recordRequest.Headers.put("Authorization",authorization);
			}
			recordRequest.sendByBufferedWriter(writer);
			return new RtspResponse(reader);
    }

    /**
     * 发送Teardown请求
     */
    public RtspResponse sendTeardown() {
        RtspRequest teardownRequest = new RtspRequest("rtsp://" + host +":"+ port + "" + path, "TEARDOWN");
        teardownRequest.Headers.put("CSeq",String.valueOf(++mCSeq));

        if(sessionId != null) {
                teardownRequest.Headers.put("Session",sessionId);
        }
        if(authorization != null) {
                teardownRequest.Headers.put("Authorization",authorization);
        }
        teardownRequest.sendByBufferedWriter(writer);
        return new RtspResponse(reader);
    }
    /**
     * 发送Play请求
     */
    public RtspResponse sendPlay() {
        RtspRequest playRequest = new RtspRequest("rtsp://" + host +":"+ port + "" + path, "PLAY");
        playRequest.Headers.put("CSeq",String.valueOf(++mCSeq));
        playRequest.Headers.put("Range", "npt=0.000-");
        if(sessionId != null) {
                playRequest.Headers.put("Session",sessionId);
        }
        if(authorization != null) {
                playRequest.Headers.put("Authorization",authorization.replace("DESCRIBE","PLAY"));
        }
        playRequest.sendByBufferedWriter(writer);
        return new RtspResponse(reader);
    }
    /**
     * 发送Pause请求
     */
    public RtspResponse sendPause() {
        RtspRequest pauseRequest = new RtspRequest("rtsp://" + host +":"+ port + "" + path, "PAUSE");
        pauseRequest.Headers.put("CSeq",String.valueOf(++mCSeq));

        if(sessionId != null) {
                pauseRequest.Headers.put("Session",sessionId);
        }
        if(authorization != null) {
                pauseRequest.Headers.put("Authorization",authorization);
        }
        pauseRequest.sendByBufferedWriter(writer);
        return new RtspResponse(reader);
    }


    public String getSPS() {
        String sSPS;
        if (sps != null ) {
            sSPS = Base64.encodeToString(sps, Base64.DEFAULT);
        } else {
            sSPS = defaultSPS;
        }
        return sSPS;
    }

    public String getPPS() {
        String sPPS;
        if (pps != null ) {
            sPPS = Base64.encodeToString(pps, Base64.DEFAULT);
        } else {
            sPPS = defaultPPS;
        }
        return sPPS;
    }

    public RtspBodyMap<String, String> createAudioBody(int trackAudio, int sampleRate, boolean isStereo) {
        RtspBodyMap<String, String> bodyMap = new RtspBodyMap<String, String>();
        int sampleRateNum = -1;
        for (int i = 0; i < AUDIO_SAMPLING_RATES.length; i++) {
          if (AUDIO_SAMPLING_RATES[i] == sampleRate) {
            sampleRateNum = i;
            break;
          }
        }
        int channel = (isStereo) ? 2 : 1;
        int config = (2 & 0x1F) << 11 | (sampleRateNum & 0x0F) << 7 | (channel & 0x0F) << 3;

        bodyMap.put("m", "audio "+ (startPort + 2 * trackAudio)+" RTP/AVP "+ RtpConstants.payloadType);
        bodyMap.put("a", "rtpmap:"+RtpConstants.payloadType+ " mpeg4-generic/"+sampleRate+"/"+channel);
        bodyMap.put("a", "fmtp:"+RtpConstants.payloadType + " streamtype=5; profile-level-id=15; mode=AAC-hbr; config=" + Integer.toHexString(config) + "; SizeLength=13; IndexLength=3; IndexDeltaLength=3;");
        bodyMap.put("a", "control:"+trackID+"=" + trackAudio);
        return bodyMap;
    }
    
    public RtspBodyMap<String, String> createVideoBody(int trackVideo, String sps, String pps) {
        RtspBodyMap<String, String> bodyMap = new RtspBodyMap<String, String>();
        bodyMap.put("m", "video "+ (startPort + 2 * trackVideo)+" RTP/AVP "+RtpConstants.payloadType);
        bodyMap.put("a", "rtpmap:"+ RtpConstants.payloadType+" H264/"+RtpConstants.clockVideoFrequency);
        bodyMap.put("a", "fmtp:"+ RtpConstants.payloadType + " packetization-mode=1;sprop-parameter-sets="+sps+","+pps+";");
        bodyMap.put("a", "control:"+trackID+"="+trackVideo);
        return bodyMap;
    }

    private String createAuth(String authResponse, String method) {
        Pattern authPattern = Pattern.compile("realm=\"(.+)\",\\s+nonce=\"(\\w+)\"", Pattern.CASE_INSENSITIVE);
        Matcher matcher = authPattern.matcher(authResponse);
        //digest auth
        if (matcher.find()) {
            String realm = matcher.group(1);
            String nonce = matcher.group(2);
            String hash1 = MD5Util.getMd5Hash(user + ":" + realm + ":" + password);
            String hash2 = MD5Util.getMd5Hash(method+ ":" + this.url);
            String hash3 = MD5Util.getMd5Hash(hash1 + ":" + nonce + ":" + hash2);
            return "Digest username=\""
                    + user
                    + "\",realm=\""
                    + realm
                    + "\",nonce=\""
                    + nonce
                    + "\",uri=\""
                    + this.url
                    + "\",response=\""
                    + hash3
                    + "\"";
            //basic auth
        } else {
            String data = user + ":" + password;
            String base64Data = Base64.encodeToString(data.getBytes(), Base64.DEFAULT);
            return "Basic " + base64Data;
        }
    }


    /* This method is used to decode pic width and height from the sps info,
     * which got from the RTSP DESCRIPE request, SDP info.
     */
    private void decodeSPS(){

        int i,offset = 32;
        int pic_width_len,pic_height_len;
        int profile_idc = sps[1];
        byte[] header_pps = new byte[pps.length];
        byte[] header_sps = new byte[sps.length];

        System.arraycopy(sps, 0, header_sps, 0, sps.length);
        System.arraycopy(pps,0,header_pps,0,pps.length);
        offset += getUeLen(sps,offset);//jump seq_parameter_set_id;

        if(profile_idc == 100 || profile_idc == 110 || profile_idc == 122
                || profile_idc == 144) {
            int chroma_format_idc = (getUeLen(sps,offset) == 1)?0:
                    ( sps[(offset+getUeLen(sps,offset))/8] >>
                            (7-((offset+getUeLen(sps,offset))%8)) );
            offset += getUeLen(sps,offset);//jump chroma_format_idc
            if(chroma_format_idc == 3)
                offset++; //jump residual_colour_transform_flag
            offset += getUeLen(sps,offset);//jump bit_depth_luma_minus8
            offset += getUeLen(sps,offset);//jump bit_depth_chroma_minus8
            offset ++; //jump qpprime_y_zero_transform_bypass_flag
            int seq_scaling_matrix_present_flag = (sps[offset/8] >> (8-(offset%8)))&0x01;
            if(seq_scaling_matrix_present_flag == 1) offset += 8; //jump seq_scaling_list_present_flag
        }
        offset += getUeLen(sps,offset);//jump log2_max_frame_num_minus4
        int pic_order_cnt_type = (getUeLen(sps,offset) == 1)?0:
                ( sps[(offset+getUeLen(sps,offset))/8] >>
                        (7-((offset+getUeLen(sps,offset))%8)) );
        offset += getUeLen(sps,offset);
        if(pic_order_cnt_type == 0) {
            offset += getUeLen(sps,offset);
        }
        else if(pic_order_cnt_type == 1) {
            offset++; //jump delta_pic_order_always_zero_flag
            offset += getUeLen(sps,offset); //jump offset_for_non_ref_pic
            offset += getUeLen(sps,offset); //jump offset_for_top_to_bottom_field
            int num_ref_frames_inpic_order_cnt_cycle = ( sps[(offset+getUeLen(sps,offset))/8] >>
                    (7-((offset+getUeLen(sps,offset))%8)) );
            for(i=0; i<num_ref_frames_inpic_order_cnt_cycle; ++i)
                offset += getUeLen(sps,offset); //jump ref_frames_inpic_order
        }
        offset += getUeLen(sps,offset); // jump num_ref_frames
        offset++; // jump gaps_in_fram_num_value_allowed_flag

        pic_width_len = getUeLen(sps,offset);

        picWidth = (getByteBit1(sps, offset + pic_width_len / 2 + 1, pic_width_len / 2)+1)*16;
        offset += pic_width_len;
        pic_height_len = getUeLen(sps,offset);

        picHeight = (getByteBit1(sps, offset + pic_height_len / 2 + 1, pic_height_len / 2)+1)*16;

    }

    private int getUeLen(byte[] bytes, int offset) {
        int zcount = 0;
        while(true) {
            if(( ( bytes[offset/8] >> (7-(offset%8)) ) & 0x01 ) == 0) {
                offset ++;
                zcount ++;
            }
            else break;
        }
        return zcount * 2 + 1;
    }

    /*
     * This method is get the bit[] from a byte[]
     * It may have a more efficient way
     */
    public int getByteBit(byte[] bytes, int offset, int len){
        int tmplen = len/8+ ((len%8+offset%8)>8?1:0) + ((offset%8 == 0)?0:1);
        int lastByteZeroNum = ((len%8+offset%8-8)>0)?(16-len%8-offset%8):(8-len%8-offset%8);
        int data = 0;
        byte tmpC = (byte) (0xFF >> (8 - lastByteZeroNum));
        byte[] tmpB = new byte[tmplen];
        byte[] tmpA = new byte[tmplen];
        int i;
        for(i = 0;i<tmplen;++i) {
            if(i == 0) tmpB[i] = (byte) (bytes[offset/8] << (offset%8) >> (offset%8));
            else if(i+1 == tmplen) tmpB[i] = (byte) ((bytes[offset/8+i] & 0xFF) >> lastByteZeroNum);
            else tmpB[i] = bytes[offset/8+i];
            tmpA[i] = (byte) ((tmpB[i] & tmpC)<<(8-lastByteZeroNum));
            if(i+1 != tmplen && i != 0) {
                tmpB[i] = (byte) ((tmpB[i]&0xFF) >> lastByteZeroNum);
                tmpB[i] = (byte) (tmpB[i] | tmpA[i-1]);
            }
            else if(i == 0) tmpB[0] = (byte) ((tmpB[0]&0xFF) >> lastByteZeroNum);
            else tmpB[i] = (byte) (tmpB[i] | tmpA[i-1]);
            data = ((tmpB[i]&0xFF) << ((tmplen-i-1)*8)) | data ;
        }
        return data-1;
    }
    // 1 2 3 4 5 6 7 8  1 2 3 4 5 6 7 8 1 2 3 4 5 6 7 8 1 2 3 4 5 6 7 8
    public int getByteBit1(byte[] bytes, int offset, int len){
        int tmplen = offset%8==0?(len+7)/8:(len/8+ ((len%8+offset%8)>8?1:0)+1);
        int lastByteZeroNum = ((len%8+offset%8-8)>0)?(16-len%8-offset%8):(8-len%8-offset%8);
        int data = 0;
        byte tmpC = (byte) (0xFF >> (8 - lastByteZeroNum));
        byte[] tmpB = new byte[tmplen];
        byte[] tmpA = new byte[tmplen];
        int i;
        for(i = 0;i<tmplen;++i) {
            if(i == 0) tmpB[i] = (byte) (((bytes[offset/8] << (offset%8))&0xff) >> (offset%8));
            else if(i+1 == tmplen) tmpB[i] = (byte) ((bytes[offset/8+i] & 0xFF) >> lastByteZeroNum <<lastByteZeroNum);
            else tmpB[i] = bytes[offset/8+i];
            data = ((tmpB[i]&0xFF) << ((tmplen-i-1)*8)) | data ;

        }
        data = data>>lastByteZeroNum;
        data += 1<<len;

        return data-1;
    }
    public int[] getPicInfo(){
        return new int[]{picWidth, picHeight};
    }

	@Override
	public void onConnectionSuccessRtp() {
		
	}

	@Override
	public void onConnectionFailedRtp(String reason) {
		
	}

	@Override
	public void onDisconnectRtp() {
		
	}

	@Override
	public void onReceiveFailRtp(String reason) {
		
	}

	@Override
	public void onReceiveSuccessRtp(RtpPackage rtpPackage) {
		
	}

	@Override
	public void onReceiveH264PackageSuccess(RtpPackage rtpPackage, H264Package h264Package) {
		/**
         * 缓存H264包
         */
        if(!h264PackageCache.containsKey(rtpPackage.Ssrc)) {
            h264PackageCache.put(rtpPackage.Ssrc, new ArrayList<H264Package>());
        }

        if(h264Package.isFU) {
            ArrayList<H264Package> array = h264PackageCache.get(rtpPackage.Ssrc);
            if(h264Package.FU.Start == 1) {
                array.clear();
                array.add(h264Package);
            } else if(h264Package.FU.End == 1) {
                array.add(h264Package);
                /** 检测排序 */
                H264Package startPackage = array.get(0);
                H264Package endPackage = array.get(array.size() -1);
                if((endPackage.SequenceNumber - startPackage.SequenceNumber + 1) == array.size()) {
                    /**
                     * 开始组包
                     */
                    array.sort(new Comparator<H264Package>() {
                        @Override
                        public int compare(H264Package o1, H264Package o2) {
                            if(o1.SequenceNumber > o2.SequenceNumber) {
                                return 1;
                            } else {
                                return -1;
                            }
                        }
                    });
                    byte[] NALU = new byte[1024*array.size()];
                    int index = 0;
                    for(int i=0;i<array.size();i++) {
                        H264Package p = array.get(i);
                        if(i==0) {
                            byte NALUType = (byte)((h264Package.Header & 0xE0)|(h264Package.FU.Header &0x1F));
                            System.arraycopy(StartCode, 0, NALU, index, 4);
                            NALU[4] = NALUType;
                            index += 5;
                            System.out.println("NALU.Type:"+ NALUType);
                        }
                        System.arraycopy(p.Payload, 0, NALU, index, p.Payload.length);
                        index += p.Payload.length;
                    }
                    
                    System.out.println("NALU.payloadSize:"+ array.size());
                    System.out.println("NALU.length:"+ index);
                    byte[] newNALU = new byte[index];

                    System.arraycopy(NALU, 0,newNALU, 0, index);
                    rtspEvent.onReceiveNALUPackage(newNALU, index, rtpPackage.getTimestamp());
                }
            } else {
                array.add(h264Package);
            }
        }
	}

	@Override
	public void onStartFailRtp(String reason) {
		
	}

	@Override
	public void onStartSuccessRtp() {
		
	}

	@Override
	public void onCloseSuccessRtp() {
		
	}
}