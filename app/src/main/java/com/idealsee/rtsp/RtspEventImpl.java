package com.idealsee.rtsp;

public class RtspEventImpl implements RtspEvent {
    @Override
    public void onConnectionSuccessRtsp(){

    }
    @Override
    public void onConnectionFailedRtsp(String reason){
        System.out.println(reason);
    }
    @Override
    public void onDisconnectRtsp(){
        
    }
    @Override
    public void onAuthErrorRtsp(){
        
    }
    @Override
    public void onAuthSuccessRtsp(){
        
    }
	@Override
	public void onReceiveNALUPackage(byte[] nalu, int naluSize, int timestamp) {
		
	}
}