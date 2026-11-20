package com.xpertsedge.hrms;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
/**
 * AES-256-GCM encryption for Gmail refresh tokens. The key comes from GMAIL_TOKEN_KEY (base64, 32 bytes) or,
 * when that is blank, is derived from the application's signing secret. It is never stored in the database.
 * The mailbox id is bound as additional authenticated data so a ciphertext cannot be moved to another mailbox.
 */
@Component
public class GmailTokenCrypto {
  private final byte[] key;
  private final SecureRandom random=new SecureRandom();
  @Autowired
  public GmailTokenCrypto(JwtService jwt,@Value("${GMAIL_TOKEN_KEY:}") String configured){this(derive(jwt,configured));}
  GmailTokenCrypto(byte[] key){
    if(key==null||key.length!=32)throw new IllegalStateException("Gmail token key must be 32 bytes.");
    this.key=key.clone();
  }
  private static byte[] derive(JwtService jwt,String configured){
    if(configured!=null&&!configured.isBlank()){
      byte[] decoded;
      try{decoded=Base64.getDecoder().decode(configured.trim());}catch(IllegalArgumentException error){throw new IllegalStateException("GMAIL_TOKEN_KEY must be base64.");}
      if(decoded.length!=32)throw new IllegalStateException("GMAIL_TOKEN_KEY must decode to exactly 32 bytes.");
      return decoded;
    }
    try{
      Mac mac=Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(jwt.mailEncryptionKey(),"HmacSHA256"));
      return mac.doFinal("xpertsedge-gmail-token-v1".getBytes(StandardCharsets.UTF_8));
    }catch(Exception error){throw new IllegalStateException("Cannot derive Gmail token key.");}
  }
  String encrypt(String plain,String context){
    try{
      byte[] nonce=new byte[12];random.nextBytes(nonce);
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      byte[] data=cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12+data.length).put(nonce).put(data).array());
    }catch(Exception error){throw new IllegalStateException("Cannot protect Gmail token.");}
  }
  String decrypt(String cipherText,String context){
    try{
      byte[] all=Base64.getDecoder().decode(cipherText);
      if(all.length<29)throw new IllegalStateException();
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(all,0,12)));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      return new String(cipher.doFinal(Arrays.copyOfRange(all,12,all.length)),StandardCharsets.UTF_8);
    }catch(Exception error){throw new IllegalStateException("Cannot read Gmail token.");}
  }
}
