package de.blinkt.openvpn.freevpn;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import static org.junit.Assert.*;
public class NodeRepositoryTest {
    @Test public void explainsConnectionFailureWithoutRawAddresses(){
        assertTrue(NodeRepository.explain(new ConnectException("private address")).contains("切换网络"));
        assertFalse(NodeRepository.explain(new ConnectException("private address")).contains("private address"));
        assertTrue(NodeRepository.explain(new SocketTimeoutException()).contains("超时"));
        assertTrue(NodeRepository.explain(new UnknownHostException()).contains("DNS"));
    }
    @Test public void invalidImportDoesNotOverwriteCache() throws Exception {
        File directory=Files.createTempDirectory("aero-test").toFile();File cache=new File(directory,"vpngate.csv");
        try {
            Files.write(cache.toPath(),"original".getBytes());
            try{new NodeRepository(directory).importList(new ByteArrayInputStream("<html>error</html>".getBytes()));fail("invalid input accepted");}catch(IOException expected){}
            assertEquals("original",new String(Files.readAllBytes(cache.toPath())));
        }finally{cache.delete();directory.delete();}
    }
}
