package com.idealsee.rtsp;

public interface RtspEvent {

  void onConnectionSuccessRtsp();

  void onConnectionFailedRtsp(String reason);

  void onDisconnectRtsp();

  void onAuthErrorRtsp();

  void onAuthSuccessRtsp();
  
}