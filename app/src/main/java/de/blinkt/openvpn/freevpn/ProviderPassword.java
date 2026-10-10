package de.blinkt.openvpn.freevpn;
import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
/** The official shared VPNBook password stays encrypted on this device. */
public final class ProviderPassword {
    private static final String ALIAS="aero_vpnbook_password";
    private static javax.crypto.SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();}
        return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
    }
    public static void save(Context context,String password)throws Exception{
        if(password.length()>128||password.contains("\n")||password.contains("\r"))throw new IllegalArgumentException("共享密码格式无效");
        if(password.isEmpty()){context.getSharedPreferences("provider",0).edit().remove("password").remove("iv").apply();return;}
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        context.getSharedPreferences("provider",0).edit().putString("password",Base64.encodeToString(encrypted,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).apply();
    }
    public static String read(Context context){
        try{android.content.SharedPreferences prefs=context.getSharedPreferences("provider",0);String data=prefs.getString("password","");if(data.isEmpty())return "";Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(data,Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){return "";}
    }
}
