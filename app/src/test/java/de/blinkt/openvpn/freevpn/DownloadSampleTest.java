package de.blinkt.openvpn.freevpn;
import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;
public class DownloadSampleTest {
    @Test public void reportsMeasuredBytesAndMbpsRatherThanAdvertisedSpeed()throws Exception {
        long[] time={0};InputStream in=new ByteArrayInputStream(new byte[1_048_576]){public synchronized int read(byte[] b,int o,int n){time[0]+=10;return super.read(b,o,n);}};
        DownloadSample.Result sample=DownloadSample.measure(in,()->time[0],()->{});
        assertEquals(1_048_576,sample.bytes);assertEquals(819200,sample.bytesPerSecond);
    }
    @Test public void stopsAfterEightSecondsWithPartialSample()throws Exception {
        long[] time={0};InputStream in=new ByteArrayInputStream(new byte[1_048_576]){public synchronized int read(byte[] b,int o,int n){time[0]+=1000;return super.read(b,o,n);}};
        DownloadSample.Result sample=DownloadSample.measure(in,()->time[0],()->{});assertEquals(65536,sample.bytes);assertEquals(8192,sample.bytesPerSecond);
    }
    @Test(expected=IOException.class) public void tinyErrorPageIsNotSpeedMeasurement()throws Exception {DownloadSample.measure(new ByteArrayInputStream(new byte[1024]),()->0,()->{});}
    @Test(expected=InterruptedException.class) public void cancellationStopsSampling()throws Exception {DownloadSample.measure(new ByteArrayInputStream(new byte[1_048_576]),()->0,()->{throw new InterruptedException();});}
}
