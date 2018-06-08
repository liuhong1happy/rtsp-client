package com.idealsee.rtp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.CharBuffer;
import java.util.concurrent.LinkedBlockingDeque;

public class RtpSocket implements Runnable {
    private DatagramSocket datagramSocket;
    private DatagramPacket recePacket;
    private Thread thread = null;
    private Integer port;
    private RtpEvent rtpEvent;
    private boolean isUDP;
    /**
     * TCP协议接收数据需要组包
     */
    private Socket rtspSocket;
    private InputStream rtspReader;
    private LinkedBlockingDeque<byte[]> tcpBufferDeque = new LinkedBlockingDeque<byte[]>();
    private Thread tcpThread;
    public class RtpBuffer {
        public int length;
        public byte[] data;
        public int chanel;
    }
    public class TcpRemainBuffer {
        public byte[] data;
        public int length;
        public int remainLength;
        public int chanel;
        public boolean isRemain;
    }


    public RtpSocket(boolean isUDP, Integer port, RtpEvent rtpEvent) {   
        this.isUDP = isUDP;
        this.port = port;
        this.rtpEvent = rtpEvent;
        if(this.isUDP) {
            this.InitUDPSocket();
        }
    }

    private void InitUDPSocket() {
        try {
            datagramSocket = new DatagramSocket(port);
            datagramSocket.setSoTimeout(60000);
            byte[] receBuf = new byte[1024];
            recePacket = new DatagramPacket(receBuf, receBuf.length);
        } catch( SocketException e) {
            System.out.println("Socket exception: "+e);
            rtpEvent.onConnectionFailedRtp(e.toString());
        }
    }

    public void setRTSPSocket(Socket rtspSocket) {
        try {
            this.rtspSocket = rtspSocket;
            this.rtspReader = rtspSocket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void receiveRTPPackege(byte[] receivedBuffer, int len) {
        RtpPackage rtpPackage = new RtpPackage(receivedBuffer, receivedBuffer.length);
        rtpPackage.printHeader();
        if(rtpPackage.PayloadType == 96) {
            // 解析H264包
            H264Package h264Package = new H264Package(rtpPackage.payload, rtpPackage.payloadSize, rtpPackage.SequenceNumber);
            h264Package.printHeader();

            this.rtpEvent.onReceiveH264PackageSuccess(rtpPackage, h264Package);
        }
        this.rtpEvent.onReceiveSuccessRtp(rtpPackage);
    }


    private void parseTcp() {
        tcpThread = new Thread(new Runnable() {
            @Override
            public void run() {
                TcpRemainBuffer remainBuffer = new TcpRemainBuffer();
                remainBuffer.isRemain = false;
                while (true) {
                    try {
                        byte[] buffer = tcpBufferDeque.take();
                        if(buffer==null) continue;
                        int index = 0;
                        while(index<buffer.length) {
                            if (buffer[index] == 0x24) {
                                RtpBuffer rtpBuffer = new RtpBuffer();
                                if(index+3>= buffer.length) break;
                                rtpBuffer.chanel = buffer[index+1];
                                rtpBuffer.length = ((buffer[index+2] & 0xFF) << 8) | (buffer[index+3] & 0xFF);
                                if(index + 4 + rtpBuffer.length > buffer.length) {
                                    remainBuffer.isRemain = true;
                                    remainBuffer.chanel = rtpBuffer.chanel;
                                    remainBuffer.length = rtpBuffer.length;
                                    remainBuffer.remainLength = buffer.length - index - 4;
                                    remainBuffer.data = new byte[remainBuffer.length];
                                    System.arraycopy(buffer, index + 4, remainBuffer.data, 0, remainBuffer.remainLength);
                                    index = buffer.length;
                                } else {
                                    remainBuffer.isRemain = false;
                                    rtpBuffer.data = new byte[rtpBuffer.length];
                                    System.arraycopy(buffer, index + 4, rtpBuffer.data, 0, rtpBuffer.length);
                                    receiveRTPPackege(rtpBuffer.data, rtpBuffer.length);
                                    index += rtpBuffer.length + 4;
                                }
                            } else if(remainBuffer.isRemain) {
                                int length = remainBuffer.length - remainBuffer.remainLength;
                                if(buffer.length<length) {
                                    length = buffer.length;
                                    System.arraycopy(buffer, index, remainBuffer.data, remainBuffer.remainLength, length);
                                    remainBuffer.remainLength += length;
                                }
                                else if((buffer.length == length) || (buffer[length] == 0x24)) {
                                    System.arraycopy(buffer, index, remainBuffer.data, remainBuffer.remainLength, length);
                                    receiveRTPPackege(remainBuffer.data, remainBuffer.length);
                                }
                                index = index + length;
                            } else break;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        tcpThread.start();
    }

    @Override
	public void run() {
        while (true) {
            try {
                byte[] receivedBuffer;
                if(this.isUDP) {
                    // 接受UDP包数据
                    datagramSocket.receive(recePacket);
                    // 获取UDP的数据
                    receivedBuffer = recePacket.getData();
                    receiveRTPPackege(receivedBuffer, receivedBuffer.length);
                } else {
                    int len;
                    byte[] buffer = new byte[10*1024];
                    while((len = rtspReader.read(buffer)) != -1) {
                        byte[] data = new byte[len];
                        System.arraycopy(buffer, 0, data, 0, len);
                        tcpBufferDeque.push(data);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                rtpEvent.onReceiveFailRtp(e.toString());
            }
        }
    }
    
    public void start() {
        if (thread == null) {  
            thread = new Thread(this);  
            thread.start();
            rtpEvent.onStartSuccessRtp();
            if(!isUDP) parseTcp();
        }  
    }

    public void close() {  
        if(this.isUDP) datagramSocket.close();
        else tcpThread = null;
        if (thread != null) {  
            thread = null;
        }
        rtpEvent.onCloseSuccessRtp();
    }  
}