package com.idealsee.rtp;

public interface RtpEvent {

  void onConnectionSuccessRtp();

  void onConnectionFailedRtp(String reason);

  void onDisconnectRtp();  

  void onReceiveFailRtp(String reason);

  void onReceiveSuccessRtp(RtpPackage rtpPackage);

  void onReceiveH264PackageSuccess(RtpPackage rtpPackage, H264Package h264Package);

  void onStartFailRtp(String reason);

  void onStartSuccessRtp();

  void onCloseSuccessRtp();
}