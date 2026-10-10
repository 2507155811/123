package de.blinkt.openvpn.freevpn;
import java.io.IOException;
/** Stop is retried until the engine has stayed inactive through a startup race. */
public final class TunnelStopper {
    public interface Transport {
        long elapsed();
        void requestStop() throws IOException;
        boolean inactive();
        void pause() throws InterruptedException;
    }
    public static void await(Transport transport,boolean launchOwned)throws IOException,InterruptedException {
        if(!launchOwned&&transport.inactive())return;
        await(transport);
    }
    public static void await(Transport transport)throws IOException,InterruptedException {
        long end=transport.elapsed()+10_000,quietSince=-1;
        while(transport.elapsed()<end){
            transport.requestStop();
            if(transport.inactive()){
                if(quietSince<0)quietSince=transport.elapsed();
                if(transport.elapsed()-quietSince>=1500)return;
            }else quietSince=-1;
            transport.pause();
        }
        throw new IOException("前一条隧道尚未确认停止");
    }
}
