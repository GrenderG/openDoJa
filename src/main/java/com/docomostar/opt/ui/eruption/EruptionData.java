package com.docomostar.opt.ui.eruption;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Little-endian records and GIF-style LZW blocks in MCM/MCT/MCA streams. */
final class EruptionData {
    static final int MAX_DATA = 64 * 1024 * 1024;
    final ByteBuffer data;
    EruptionData(byte[] bytes) { data=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); }
    int u8() { return data.get()&255; }
    int u16() { return data.getShort()&65535; }
    int i32() { return data.getInt(); }
    float f32() { return data.getFloat(); }
    int remaining() { return data.remaining(); }
    byte[] bytes(int n) {
        if(n<0||n>MAX_DATA||n>remaining()) throw new IllegalArgumentException("Eruption data size");
        byte[] bytes=new byte[n]; data.get(bytes); return bytes;
    }
    EruptionData record() {
        int start=data.position(), length=i32();
        if(length<16||length>MAX_DATA||length-4>remaining()) throw new IllegalArgumentException("Eruption record size");
        data.position(start); return new EruptionData(bytes(length));
    }
    EruptionData payload(boolean compressed,int size) throws IOException {
        byte[] bytes=bytes(remaining());
        return new EruptionData(compressed?decodeLzw(bytes,size):bytes);
    }
    static byte[] decodeLzw(byte[] blocks,int expected) throws IOException {
        if(expected<0||expected>MAX_DATA||blocks.length<2) throw new IOException("Invalid Eruption LZW size");
        int minimum=blocks[0]&255;
        if(minimum<2||minimum>11) throw new IOException("Invalid Eruption LZW code size");
        byte[] packed=new byte[blocks.length]; int packedSize=0, p=1;
        while(p<blocks.length) {
            int length=blocks[p++]&255;
            if(length==0) break;
            if(length>blocks.length-p) throw new IOException("Truncated Eruption LZW block");
            System.arraycopy(blocks,p,packed,packedSize,length); p+=length; packedSize+=length;
        }
        byte[] output=new byte[expected], suffix=new byte[4096], stack=new byte[4096];
        int[] prefix=new int[4096];
        int clear=1<<minimum,end=clear+1,next=end+1,width=minimum+1,old=-1,first=0,out=0,bit=0;
        for(int i=0;i<clear;i++) suffix[i]=(byte)i;
        boolean ended=false;
        while(bit+width<=packedSize*8) {
            int at=bit>>>3, word=packed[at]&255;
            if(at+1<packedSize) word|=(packed[at+1]&255)<<8;
            if(at+2<packedSize) word|=(packed[at+2]&255)<<16;
            int code=(word>>>(bit&7))&((1<<width)-1); bit+=width;
            if(code==clear) { next=end+1;width=minimum+1;old=-1;continue; }
            if(code==end) { ended=true;break; }
            int current=code,top=0;
            if(current==next&&old>=0) { stack[top++]=(byte)first;current=old; }
            else if(current>=next) throw new IOException("Invalid Eruption LZW dictionary reference");
            while(current>=clear) {
                if(top==stack.length||current>=next) throw new IOException("Invalid Eruption LZW chain");
                stack[top++]=suffix[current];current=prefix[current];
            }
            first=current; stack[top++]=(byte)first;
            if(top>output.length-out) throw new IOException("Eruption LZW output exceeds declared size");
            while(top>0) output[out++]=stack[--top];
            if(old>=0&&next<4096) {
                prefix[next]=old;suffix[next++]=(byte)first;
                if(next==(1<<width)&&width<12) width++;
            }
            old=code;
        }
        if(!ended||out!=expected) throw new IOException("Truncated Eruption LZW output: "+out+"/"+expected);
        return output;
    }
}
