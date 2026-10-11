package de.blinkt.openvpn.freevpn;
import org.json.*;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.Files;import java.security.*;import java.security.cert.*;import java.util.*;import java.util.regex.*;
import javax.net.ssl.*;
/** Official anonymous LEAP v3 API, with a pinned provider CA and ordinary hostname verification. */
public final class RiseupRepository {
    public static final String API="https://api.black.riseup.net:4430/3/";
    private static final String CA="-----BEGIN CERTIFICATE-----\nMIIBYjCCAQigAwIBAgIBATAKBggqhkjOPQQDAjAXMRUwEwYDVQQDEwxMRUFQIFJv\nb3QgQ0EwHhcNMjExMTAyMTkwNTM3WhcNMjYxMTAyMTkxMDM3WjAXMRUwEwYDVQQD\nEwxMRUFQIFJvb3QgQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQxOXBGu+gf\npjHzVteGTWL6XnFxtEnKMFpKaJkA/VOHmESzoLsZRQxt88GssxaqC01J17idQiqv\nzgNpedmtvFtyo0UwQzAOBgNVHQ8BAf8EBAMCAqQwEgYDVR0TAQH/BAgwBgEB/wIB\nATAdBgNVHQ4EFgQUZdoUlJrCIUNFrpffAq+LQjnwEz4wCgYIKoZIzj0EAwIDSAAw\nRQIgfr3w4tnRG+NdI3LsGPlsRktGK20xHTzsB3orB0yC6cICIQCB+/9y8nmSStfN\nVUMUyk2hNd7/kC8nL222TTD7VZUtsg==\n-----END CERTIFICATE-----\n";
    private final File cache;
    public RiseupRepository(File directory){cache=new File(directory,"riseup-private.json");}
    private static X509Certificate certificate(String pem)throws IOException {
        try{return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));}catch(CertificateException e){throw new IOException("Riseup 证书无效",e);}
    }
    static List<Node> nodes(JSONObject directory,String credentials,String ca)throws IOException {
        try{
            if(directory==null)throw new IOException("Riseup 缓存目录无效");
            String cert=pemBlock(credentials,"CERTIFICATE"),key=pemBlock(credentials,"(?:RSA |EC )?PRIVATE KEY");
            certificate(cert).checkValidity();certificate(ca).checkValidity();
            JSONObject locations=directory.getJSONObject("locations"),options=directory.getJSONObject("openvpn_configuration");JSONArray gateways=directory.getJSONArray("gateways");
            if(gateways.length()>200)throw new IOException("Riseup 目录过大");
            List<Node> result=new ArrayList<>();
            for(int i=0;i<gateways.length();i++){
                JSONObject gateway=gateways.getJSONObject(i),location=locations.getJSONObject(gateway.getString("location")),caps=gateway.getJSONObject("capabilities");
                String ip=gateway.getString("ip_address"),code=location.getString("country_code").toUpperCase(Locale.ROOT);
                if(!NodeParser.publicIpv4(ip)||!code.matches("[A-Z]{2}")||Arrays.asList("CN","HK","MO").contains(code))continue;
                JSONArray transports=caps.getJSONArray("transport");boolean openvpn=false;for(int j=0;j<transports.length();j++)if("openvpn".equals(transports.optString(j)))openvpn=true;if(!openvpn)continue;
                JSONArray protocols=caps.getJSONArray("protocols");boolean tcp=false;for(int j=0;j<protocols.length();j++)if("tcp".equals(protocols.getString(j)))tcp=true;if(!tcp)continue;
                JSONArray ports=caps.getJSONArray("ports");if(ports.length()==0)continue;int port=Integer.parseInt(String.valueOf(ports.get(0)));if(port<1||port>65535)continue;
                StringBuilder config=new StringBuilder("client\ndev tun\nproto tcp-client\nremote "+ip+" "+port+"\nnobind\npersist-key\npersist-tun\nremote-cert-tls server\nredirect-gateway def1\nverb 3\n");
                for(String name:Arrays.asList("auth","cipher","data-ciphers","tls-cipher","keepalive")){if(options.has(name)){String value=options.getString(name);if(!value.matches("[A-Za-z0-9_:+. /-]{1,256}"))throw new IOException("Riseup 参数无效");config.append(name).append(' ').append(value).append('\n');}}
                if(!options.has("data-ciphers")&&"AES-128-CBC".equals(options.optString("cipher")))config.append("data-ciphers AES-256-GCM:AES-128-GCM:AES-128-CBC\n");
                config.append("<ca>\n").append(ca.trim()).append("\n</ca>\n<cert>\n").append(cert.trim()).append("\n</cert>\n<key>\n").append(key.trim()).append("\n</key>\n");
                if(!NodeParser.safeConfig(config.toString(),ip))throw new IOException("Riseup 配置校验失败");
                result.add(new Node("riseup-"+ip,ip,location.getString("name"),code,Integer.MAX_VALUE,0,0,0,config.toString()));
            }
            if(result.isEmpty())throw new IOException("Riseup 没有支持的境外 TCP 节点");return result;
        }catch(JSONException|NumberFormatException|CertificateException e){throw new IOException("Riseup 数据或证书无效",e);}
    }
    private static String pemBlock(String text,String kind)throws IOException {
        Matcher m=Pattern.compile("-----BEGIN ("+kind+")-----[\\s\\S]*?-----END \\1-----").matcher(text);
        if(!m.find())throw new IOException("Riseup 未返回有效的匿名证书");return m.group();
    }
    public List<Node> load(boolean network)throws IOException {
        if(!network){JSONObject cached=json(new String(Files.readAllBytes(cache.toPath()),StandardCharsets.UTF_8));long at=cached.optLong("at",0);if(at<=0||at>System.currentTimeMillis()+300000||System.currentTimeMillis()-at>21600000)throw new IOException("Riseup 缓存已过期");return nodes(cached.optJSONObject("directory"),cached.optString("credentials"),CA);}
        JSONObject directory=json(fetch("config/eip-service.json"));String credentials=fetch("cert");List<Node> result=nodes(directory,credentials,CA);
        try{JSONObject stored=new JSONObject().put("at",System.currentTimeMillis()).put("directory",directory).put("credentials",credentials);Files.write(cache.toPath(),stored.toString().getBytes(StandardCharsets.UTF_8));}catch(JSONException e){throw new IOException("Riseup 缓存失败",e);}return result;
    }
    private static JSONObject json(String value)throws IOException {try{return new JSONObject(value);}catch(JSONException e){throw new IOException("Riseup 目录无效",e);}}
    private static String fetch(String path)throws IOException {
        HttpsURLConnection connection=(HttpsURLConnection)new URL(API+path).openConnection();
        try{
            X509Certificate root=certificate(CA);root.checkValidity();
            KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null);trust.setCertificateEntry("riseup",root);
            TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(trust);SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,factory.getTrustManagers(),null);connection.setSSLSocketFactory(tls.getSocketFactory());
            connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","AeroVPN/1.7");
            if(connection.getResponseCode()!=200)throw new IOException("Riseup API 暂不可用");
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>1000000)throw new IOException("Riseup 数据过大");out.write(buffer,0,n);}return out.toString("UTF-8");}
        }catch(GeneralSecurityException e){throw new IOException("Riseup CA 校验失败，请更新应用",e);}finally{connection.disconnect();}
    }
}
