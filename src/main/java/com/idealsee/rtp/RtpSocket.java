package com.idealsee.rtp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class RtpSocket implements Runnable {
    private DatagramSocket datagramSocket;
    private DatagramPacket recePacket;
    private Thread thread = null;

    public RtpSocket(Integer port) {
        try {
            datagramSocket = new DatagramSocket(port);
            datagramSocket.setSoTimeout(60000);
            byte[] receBuf = new byte[1024];
            recePacket = new DatagramPacket(receBuf, receBuf.length);
        } catch( SocketException e) {
            System.out.println("Socket exception: "+e);
        }
    }

	@Override
	public void run() {
        while (true) {
            try {
                // 接受UDP包数据
                datagramSocket.receive(recePacket);
                // 获取UDP的数据
                RtpPackage rtpPackage = new RtpPackage(recePacket.getData(), recePacket.getLength());
                
                rtpPackage.printHeader();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public void start() {
        if (thread == null) {  
            thread = new Thread(this);  
            thread.start();  
        }  
    }

    public void close() {  
        datagramSocket.close();  
        if (thread != null) {  
            thread = null;
        }
    }  
}