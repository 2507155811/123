package de.blinkt.openvpn.freevpn;
/** TCP connection checks cannot certify an OpenVPN tunnel or a UDP server. */
public final class TcpEndpoint {
    public static int port(String config){
        String proto="udp";String[] remote=null;
        for(String line:config.split("\\r?\\n")){String[] words=line.trim().split("\\s+");if(words.length>=2&&words[0].equals("proto"))proto=words[1];if(words.length>=3&&words[0].equals("remote")&&remote==null)remote=words;}
        if(remote==null)return -1;String effective=remote.length>=4?remote[3]:proto;
        if(!effective.startsWith("tcp"))return -1;
        try{int port=Integer.parseInt(remote[2]);return port>=1&&port<=65535?port:-1;}catch(NumberFormatException e){return -1;}
    }
}
