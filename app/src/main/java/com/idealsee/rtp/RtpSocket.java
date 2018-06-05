package com.idealsee.rtp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class RtpSocket implements Runnable {
    private DatagramSocket datagramSocket;
    private DatagramPacket recePacket;
    private Thread thread = null;
    private Integer port;
    private RtpEvent rtpEvent;
    
    public RtpSocket(Integer port,  RtpEvent rtpEvent) {
        try {
            datagramSocket = new DatagramSocket(port);
            datagramSocket.setSoTimeout(60000);
            byte[] receBuf = new byte[1024];
            recePacket = new DatagramPacket(receBuf, receBuf.length);
            this.port = port;
            this.rtpEvent = rtpEvent;
        } catch( SocketException e) {
            System.out.println("Socket exception: "+e);
            rtpEvent.onConnectionFailedRtp(e.toString());
        }
    }

	@Override
	public void run() {
        while (true) {
            try {
                // 接受UDP包数�?
                datagramSocket.receive(recePacket);
                // 获取UDP的数�?
                RtpPackage rtpPackage = new RtpPackage(recePacket.getData(), recePacket.getLength());
                
                rtpPackage.printHeader();
                if(rtpPackage.PayloadType == 96) {
                    // 解析H264�?
                    H264Package h264Package = new H264Package(rtpPackage.payload, rtpPackage.payloadSize, rtpPackage.SequenceNumber);
                    h264Package.printHeader();

                    this.rtpEvent.onReceiveH264PackageSuccess(rtpPackage, h264Package);
                }
                this.rtpEvent.onReceiveSuccessRtp(rtpPackage);
            } catch (IOException e) {
                e.printStackTrace();
                rtpEvent.onReceiveFailRtp(e.toString());
            }
        }
    }
    
    public void start() {
        // SocketAddress address = new InetSocketAddress(this.port);
        // try {
		// 	datagramSocket.connect(address);
		// } catch (SocketException e) {
        //     e.printStackTrace();
        //     rtpEvent.onStartFailRtp(e.toString());
		// }
        if (thread == null) {  
            thread = new Thread(this);  
            thread.start();  
            rtpEvent.onStartSuccessRtp();
        }  
    }

    public void close() {  
        datagramSocket.close();  
        if (thread != null) {  
            thread = null;
        }
        rtpEvent.onCloseSuccessRtp();
    }  
}