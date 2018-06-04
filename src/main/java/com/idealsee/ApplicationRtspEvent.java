package com.idealsee;

import com.idealsee.rtsp.RtspEvent;

//rtsp://127.0.0.1:5050/ok

public class ApplicationRtspEvent implements RtspEvent {

	@Override
	public void onConnectionSuccessRtsp() {
		App.rtspClient.Play();
	}

	@Override
	public void onConnectionFailedRtsp(String reason) {
		
	}

	@Override
	public void onDisconnectRtsp() {
		
	}

	@Override
	public void onAuthErrorRtsp() {
		
	}

	@Override
	public void onAuthSuccessRtsp() {
		
	}

	@Override
	public void onReceiveNALUPackage(byte[] nalu, int naluSize) {
		
	}

}