package com.commoditycx.security;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TotpServiceTest {
 @Test void verifiesRfc6238Sha1TestVector(){TotpService service=new TotpService("");String secret="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";assertTrue(service.verifyAt(secret,"287082",59));assertFalse(service.verifyAt(secret,"000000",59));}
 @Test void rejectsMalformedCodes(){TotpService service=new TotpService("");assertFalse(service.verifyAt("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","12345",59));assertFalse(service.verifyAt("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","ABCDEF",59));}
}
