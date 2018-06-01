package com.idealsee.rtp;

public class FragmentationUnit {
    public byte Header;
    public int Start;
    public int End;
    public int Remain;
    public int Type;
    public FragmentationUnit(byte header) {
        Header = header;

        Start = (Header & 0xFF) >> 7;
        End = (Header & 0x7F) >> 6;
        Remain = (Header & 0x3F) >> 5;
        Type = Header & 0x1F;
    }

    public void printHeader() {
        System.out.print("[FU-Header] ");
        System.out.println("S: " + Start
                           + ", E: " + End 
                           + ", R: " + Remain 
                           + ", Type: " + Type);
    }
}