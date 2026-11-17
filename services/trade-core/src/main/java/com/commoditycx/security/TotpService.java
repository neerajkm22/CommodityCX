package com.commoditycx.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class TotpService {
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final byte[] encryptionKey;
    public TotpService(@Value("${MFA_ENCRYPTION_KEY:}") String key) {
        if(key==null || key.isBlank()) this.encryptionKey=null;
        else {
            try { this.encryptionKey=java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)); }
            catch(Exception e){throw new IllegalStateException(e);}
        }
    }
    public String newSecret() { byte[] b=new byte[20];new SecureRandom().nextBytes(b);return base32(b); }
    public String encrypt(String secret) {
        if(encryptionKey==null) throw new IllegalStateException("MFA_ENCRYPTION_KEY must be configured before enabling MFA");
        try { byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(encryptionKey,"AES"),new GCMParameterSpec(128,iv));
            byte[] encrypted=c.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length+encrypted.length).put(iv).put(encrypted).array());
        } catch(Exception e){throw new IllegalStateException("Unable to protect MFA secret",e);}
    }
    public String decrypt(String encoded) {
        if(encryptionKey==null) throw new IllegalStateException("MFA_ENCRYPTION_KEY is not configured");
        try { byte[] all=Base64.getDecoder().decode(encoded);ByteBuffer b=ByteBuffer.wrap(all);byte[] iv=new byte[12];b.get(iv);byte[] data=new byte[b.remaining()];b.get(data);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(encryptionKey,"AES"),new GCMParameterSpec(128,iv));
            return new String(c.doFinal(data),StandardCharsets.UTF_8);
        } catch(Exception e){throw new IllegalStateException("Unable to decrypt MFA secret",e);}
    }
    public boolean verify(String secret,String code) { return verifyAt(secret,code,Instant.now().getEpochSecond()); }
    public boolean verifyAt(String secret,String code,long epochSeconds) {
        if(code==null || !code.matches("\\d{6}")) return false;
        long counter=epochSeconds/30;
        for(long t=counter-1;t<=counter+1;t++) if(code.equals(code(secret,t))) return true;
        return false;
    }
    public String provisioningUri(String email,String secret,String issuer) {
        return "otpauth://totp/"+url(issuer)+":"+url(email)+"?secret="+secret+"&issuer="+url(issuer)+"&algorithm=SHA1&digits=6&period=30";
    }
    private String code(String secret,long counter) {
        try { byte[] key=unbase32(secret);Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(key,"HmacSHA1"));
            byte[] h=mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());int o=h[h.length-1]&15;
            int n=((h[o]&127)<<24)|((h[o+1]&255)<<16)|((h[o+2]&255)<<8)|(h[o+3]&255);
            return String.format("%06d",n%1_000_000);
        } catch(Exception e){throw new IllegalArgumentException("Invalid TOTP secret",e);}
    }
    private static String base32(byte[] bytes) {
        StringBuilder out=new StringBuilder();int buffer=0,bits=0;
        for(byte value:bytes){buffer=(buffer<<8)|(value&255);bits+=8;while(bits>=5){out.append(ALPHABET.charAt((buffer>>(bits-5))&31));bits-=5;}}
        if(bits>0)out.append(ALPHABET.charAt((buffer<<(5-bits))&31));return out.toString();
    }
    private static byte[] unbase32(String s) {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();int buffer=0,bits=0;
        for(char c:s.toUpperCase().toCharArray()){int v=ALPHABET.indexOf(c);if(v<0)continue;buffer=(buffer<<5)|v;bits+=5;if(bits>=8){out.write((buffer>>(bits-8))&255);bits-=8;}}
        return out.toByteArray();
    }
    private static String url(String s){return java.net.URLEncoder.encode(s,StandardCharsets.UTF_8);}
}
