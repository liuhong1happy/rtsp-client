package com.idealsee.rtp;


public class H264Package {
    public int HEADER_SIZE = 1;
    public byte[] Header;
    public int ForbiddenZeroBit;
    public int NALRefIdc;
    public int Type;
    public byte[] Payload;
    public int PayloadSize;
    public boolean isFU = false;
    public FragmentationUnit FU;
    /**
     * 24    STAP-A   单一时间的组合包
     * 25    STAP-B   单一时间的组合包
     * 26    MTAP16   多个时间的组合包
     * 27    MTAP24   多个时间的组合包
     * 28    FU-A     分片的单元
     * 29    FU-B     分片的单元
     */
    public H264Package(byte[] payload, int packet_size) {
        Header = new byte[1];
        Header[0] = payload[0];

        ForbiddenZeroBit = (Header[0] & 0xFF) >> 7;
        NALRefIdc = (Header[0] & 0x7F) >> 5;
        Type = Header[0] & 0x1F;

        if(Type == 28 || Type == 29) {
            HEADER_SIZE = 2;
            isFU = true;
            FU = new FragmentationUnit(payload[1]);
            FU.printHeader();
        }

        //get the payload bitstream:
        PayloadSize = packet_size - HEADER_SIZE;
        Payload = new byte[PayloadSize];
        for (int i=HEADER_SIZE; i < packet_size; i++) Payload[i-HEADER_SIZE] = payload[i];
    }


    public void printHeader() {
        System.out.print("[H264-Header] ");
        System.out.println("F: " + ForbiddenZeroBit
                           + ", NRI: " + NALRefIdc 
                           + ", Type: " + Type);
    }
}