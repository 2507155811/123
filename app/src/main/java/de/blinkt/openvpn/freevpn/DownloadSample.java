package de.blinkt.openvpn.freevpn;
import java.io.*;
/** Measures response-body transfer only; connection setup is excluded. */
public final class DownloadSample {
    public interface Clock {long millis();}
    public interface Cancellation {void check()throws InterruptedException;}
    public static final class Result {
        public final long bytes,bytesPerSecond;
        Result(long bytes,long elapsed){this.bytes=bytes;bytesPerSecond=bytes*1000L/Math.max(1,elapsed);}
    }
    public static Result measure(InputStream input,Clock clock,Cancellation cancel)throws IOException,InterruptedException {
        long start=clock.millis();int total=0,n;byte[] buffer=new byte[8192];
        while(total<1_048_576){
            cancel.check();if(clock.millis()-start>=8_000)break;
            try{n=input.read(buffer,0,Math.min(buffer.length,1_048_576-total));}catch(IOException e){if(total<65536)throw e;break;}
            if(n<0)break;if(n==0)continue;total+=n;
        }
        cancel.check();if(total<65536)throw new IOException("测速样本不足 64 KB");
        return new Result(total,clock.millis()-start);
    }
}
