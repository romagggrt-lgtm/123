package dev.zlor.rules;

import java.security.*;
import java.util.HexFormat;

/** Bounded single-stream Ogg Vorbis container validation before native/client decoding. */
public final class OggClip {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private OggClip() { }
    public static boolean validHash(String hash) { return hash != null && hash.matches("[a-f0-9]{64}"); }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static double validate(byte[] bytes) {
        if (bytes.length < 64 || bytes.length > AudioLimits.bytes()) throw bad("OGG превышает разрешённый размер.");
        int at = 0, sequence = 0, serial = 0, rate = 0; long samples = -1; boolean eos = false;
        while (at < bytes.length) {
            if (eos || at + 27 > bytes.length || bytes[at]!='O' || bytes[at+1]!='g' || bytes[at+2]!='g' || bytes[at+3]!='S' || bytes[at+4]!=0) throw bad("Повреждённый контейнер OGG.");
            int flags = bytes[at+5] & 255, n = bytes[at+26] & 255, header = 27+n;
            if (at + header > bytes.length) throw bad("Обрезанный заголовок OGG.");
            int body = 0; for (int i=0;i<n;i++) body += bytes[at+27+i]&255;
            int end = at+header+body; if (end>bytes.length) throw bad("Обрезанный файл OGG.");
            if (i32(bytes,at+18)!=sequence) throw bad("Неверный порядок страниц OGG.");
            int stream = i32(bytes,at+14);
            if (sequence==0) {
                if (flags!=2 || body<30 || n==0 || (bytes[at+27]&255)!=30) throw bad("Нужен одиночный поток OGG Vorbis.");
                serial=stream;int p=at+header;
                byte[] magic={1,'v','o','r','b','i','s'};
                for(int i=0;i<magic.length;i++)if(bytes[p+i]!=magic[i])throw bad("Нужен OGG Vorbis, не Opus и не MP3.");
                int channels=bytes[p+11]&255;rate=i32(bytes,p+12);
                if(i32(bytes,p+7)!=0 || channels<1 || channels>2 || rate<8000 || rate>48000 || (bytes[p+29]&1)==0) throw bad("Vorbis: моно/стерео, частота 8–48 кГц.");
            } else if (stream!=serial || (flags&2)!=0) throw bad("Несколько потоков OGG не поддерживаются.");
            if((flags&~7)!=0)throw bad("Неверный заголовок OGG.");
            int crc=0;
            for(int p=at;p<end;p++){int value=p>=at+22&&p<at+26?0:bytes[p]&255;crc^=value<<24;for(int bit=0;bit<8;bit++)crc=(crc<<1)^((crc<0)?0x04c11db7:0);}
            if(crc!=i32(bytes,at+22))throw bad("Не совпала контрольная сумма OGG.");
            long granule=i64(bytes,at+6);
            if(granule>=0){if(granule<samples || granule>(long)rate*AudioLimits.seconds())throw bad("Звук превышает разрешённую длительность.");samples=granule;}
            eos=(flags&4)!=0;at=end;sequence++;
        }
        if(!eos || samples<=0)throw bad("OGG не содержит завершённого звука.");
        return (double)samples/rate;
    }
    private static int i32(byte[] a,int p){return(a[p]&255)|((a[p+1]&255)<<8)|((a[p+2]&255)<<16)|((a[p+3]&255)<<24);}
    private static long i64(byte[] a,int p){return Integer.toUnsignedLong(i32(a,p))|((long)i32(a,p+4)<<32);}
    private static IllegalArgumentException bad(String message){return new IllegalArgumentException(message);}
}
