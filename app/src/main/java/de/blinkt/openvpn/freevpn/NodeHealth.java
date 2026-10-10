package de.blinkt.openvpn.freevpn;

/** Evidence from this phone's VPN-bound HTTPS requests, never source-reported statistics. */
public final class NodeHealth {
    public static final long VALID_FOR=10*60*1000L;
    public final Node node;
    public final long checkedAt,latencyMs,bytesPerSecond;
    public NodeHealth(Node node,long checkedAt,long latencyMs,long bytesPerSecond){this.node=node;this.checkedAt=checkedAt;this.latencyMs=latencyMs;this.bytesPerSecond=bytesPerSecond;}
    public boolean fresh(long now){return checkedAt<=now && now-checkedAt<VALID_FOR;}
    public double quality(){return bytesPerSecond>0?bytesPerSecond/(1.0+latencyMs/500.0):1000.0/(1+latencyMs);}
    public boolean substantiallyBetterThan(NodeHealth current){return current==null || quality()>current.quality()*1.2;}
}
