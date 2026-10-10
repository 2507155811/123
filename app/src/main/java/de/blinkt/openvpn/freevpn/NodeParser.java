package de.blinkt.openvpn.freevpn;

import java.util.*;
import java.nio.charset.StandardCharsets;

/** VPN Gate's UTF-8 CSV; never invent entries or trust executable profile directives. */
public final class NodeParser {
    private static final Set<String> EXCLUDED = new HashSet<>(Arrays.asList("CN", "HK", "MO"));
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "auth-user-pass","fast-io","client","dev","dev-type","proto","remote","resolv-retry","nobind","persist-key",
            "persist-tun","cipher","data-ciphers","data-ciphers-fallback","auth","verb","mute",
            "remote-cert-tls","verify-x509-name","tls-version-min","tls-version-max","tls-cipher",
            "tls-ciphersuites","key-direction","auth-nocache","explicit-exit-notify","tun-mtu",
            "mssfix","reneg-sec","keepalive","ping","ping-restart","connect-retry",
            "connect-retry-max","connect-timeout","sndbuf","rcvbuf","comp-lzo","compress",
            "allow-compression","pull","route","route-ipv6","redirect-gateway","dhcp-option",
            "route-delay","route-metric","float","push-peer-info","setenv","remote-random"));
    public static List<Node> parse(String csv) {
        if (csv.length()>12_000_000) throw new IllegalArgumentException("节点列表过大");
        List<Node> result=new ArrayList<>(); Set<String> seen=new HashSet<>();
        Map<String,Integer> header=null;
        for (String line:csv.split("\r?\n")) {
            if (line.startsWith("#HostName,")) {
                List<String> cols=columns(line.substring(1)); header=new HashMap<>();
                for(int i=0;i<cols.size();i++) header.put(cols.get(i),i);
                continue;
            }
            if (line.isBlank() || line.startsWith("#") || line.startsWith("*") || header==null) continue;
            try {
                List<String> cols=columns(line);
                String code=field(cols,header,"CountryShort").trim().toUpperCase(Locale.ROOT);
                String ip=field(cols,header,"IP").trim();
                if (!code.matches("[A-Z]{2}") || EXCLUDED.contains(code) || !publicIpv4(ip)) continue;
                String encoded=field(cols,header,"OpenVPN_ConfigData_Base64");
                if (encoded.length()>400_000) continue;
                String config=new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8);
                if (!safeConfig(config,ip) || !seen.add(ip)) continue;
                String latency=field(cols,header,"Ping");
                int ping=latency.isBlank()?Integer.MAX_VALUE:Integer.parseInt(latency);
                result.add(new Node(field(cols,header,"HostName"),ip,field(cols,header,"CountryLong"),code,
                        ping<0?Integer.MAX_VALUE:ping,Math.max(0,Long.parseLong(field(cols,header,"Speed"))),
                        Math.max(0,Integer.parseInt(field(cols,header,"NumVpnSessions"))),
                        Long.parseLong(field(cols,header,"Score")),config));
            } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) { /* Drop malformed rows, retain valid rows. */ }
        }
        if (header==null) throw new IllegalArgumentException("节点源没有返回有效的 VPN Gate 列表");
        return result;
    }
    private static String field(List<String> cols,Map<String,Integer> header,String name) {
        Integer i=header.get(name); if(i==null) throw new IllegalArgumentException("缺少字段 "+name);
        return cols.get(i);
    }
    static List<String> columns(String line) {
        List<String> out=new ArrayList<>();StringBuilder value=new StringBuilder();boolean quoted=false;
        for(int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if(c=='"') { if(quoted && i+1<line.length() && line.charAt(i+1)=='"'){value.append('"');i++;}else quoted=!quoted; }
            else if(c==',' && !quoted) {out.add(value.toString());value.setLength(0);}else value.append(c);
        }
        if(quoted) throw new IllegalArgumentException("CSV 引号不完整");
        out.add(value.toString());return out;
    }
    public static boolean publicIpv4(String ip) {
        if(!ip.matches("\\d{1,3}(\\.\\d{1,3}){3}"))return false;
        String[] p=ip.split("\\."); int a=Integer.parseInt(p[0]),b=Integer.parseInt(p[1]);
        for(String s:p)if(Integer.parseInt(s)>255)return false;
        return a>0 && a<224 && a!=10 && a!=127 && !(a==169&&b==254) && !(a==172&&b>=16&&b<=31)
                && !(a==192&&b==168) && !(a==100&&b>=64&&b<=127) && !(a==198&&(b==18||b==19));
    }
    public static boolean safeConfig(String config,String ip) {
        if(config.length()>300_000 || config.indexOf('\0')>=0)return false;
        String block=null;boolean ca=false,remote=false,client=false;
        for(String raw:config.split("\r?\n")) {
            String line=raw.trim();
            if(line.isEmpty() || line.startsWith("#") || line.startsWith(";"))continue;
            if(block!=null) { if(line.equals("</"+block+">"))block=null; continue; }
            if(line.startsWith("<")) {
                if(!Arrays.asList("<ca>","<cert>","<key>","<tls-auth>","<tls-crypt>").contains(line))return false;
                block=line.substring(1,line.length()-1);if(block.equals("ca"))ca=true;continue;
            }
            String[] words=line.split("\\s+");String directive=words[0].toLowerCase(Locale.ROOT);
            if(!ALLOWED.contains(directive))return false;
            if((directive.equals("auth-user-pass")||directive.equals("fast-io"))&&words.length!=1)return false;
            if(directive.equals("setenv") && (words.length<2 || !words[1].equals("CLIENT_CERT")))return false;
            if(directive.equals("dev") && (words.length!=2 || !words[1].equals("tun")))return false;
            if(directive.equals("client"))client=true;
            if(directive.equals("remote")) {
                if(words.length<3 || !words[1].equals(ip))return false;
                try {int port=Integer.parseInt(words[2]);if(port<1||port>65535)return false;} catch(NumberFormatException e){return false;}
                remote=true;
            }
        }
        return block==null && ca && remote && client;
    }
}
