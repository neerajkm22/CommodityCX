package com.commoditycx.api;
import com.commoditycx.security.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class AuthController {
 private final JdbcTemplate jdbc;private final PasswordEncoder passwords;private final JwtService jwt;private final TotpService totp;
 public AuthController(JdbcTemplate jdbc,PasswordEncoder passwords,JwtService jwt,TotpService totp){this.jdbc=jdbc;this.passwords=passwords;this.jwt=jwt;this.totp=totp;}
 public record RegisterRequest(@NotBlank @Size(max=160)String fullName,@Email @NotBlank String email,@NotBlank @Size(min=12,max=72)String password,@NotBlank String legalName,@Size(min=2,max=2)String countryCode){}
 public record LoginRequest(@Email @NotBlank String email,@NotBlank String password,String mfaCode){}
 public record RefreshRequest(@NotBlank String refreshToken){}
 @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public Map<String,Object> register(@Valid @RequestBody RegisterRequest in){
  String code="P-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
  UUID participant=jdbc.queryForObject("insert into participants(code,legal_name,country_code) values (?,?,?) returning id",UUID.class,code,in.legalName(),in.countryCode().toUpperCase(Locale.ROOT));
  UUID user=jdbc.queryForObject("insert into users(participant_id,email,full_name,password_hash) values (?,?,?,?) returning id",UUID.class,participant,in.email().toLowerCase(Locale.ROOT),in.fullName(),passwords.encode(in.password()));
  jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where name='TRADER'",user);
  audit(user,participant,"PARTICIPANT_REGISTERED","PARTICIPANT",participant.toString());
  return Map.of("participantId",participant,"participantCode",code,"userId",user,"status","PENDING_APPROVAL");
 }
 @PostMapping("/auth/login") @Transactional
 public Map<String,Object> login(@Valid @RequestBody LoginRequest in){
  var rows=jdbc.queryForList("select id,password_hash,enabled,participant_id,email,mfa_enabled,mfa_secret_encrypted from users where email=?",in.email().toLowerCase(Locale.ROOT));
  if(rows.isEmpty()||!passwords.matches(in.password(),(String)rows.get(0).get("password_hash")))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
  var u=rows.get(0);if(!Boolean.TRUE.equals(u.get("enabled")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account pending approval or disabled");
  if(Boolean.TRUE.equals(u.get("mfa_enabled"))&&!totp.verify(totp.decrypt((String)u.get("mfa_secret_encrypted)),in.mfaCode()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Valid MFA code required");
  UUID id=(UUID)u.get("id");List<String> roles=roles(id);jdbc.update("update users set last_login_at=now() where id=?",id);
  return tokenResponse(id,(String)u.get("email"),u.get("participant_id"),roles);
 }
 @PostMapping("/auth/refresh") @Transactional
 public Map<String,Object> refresh(@Valid @RequestBody RefreshRequest in){
  String hash=sha(in.refreshToken());var rows=jdbc.queryForList("select id,user_id,expires_at,revoked_at from refresh_tokens where token_hash=? for update",hash);
  if(rows.isEmpty()||rows.get(0).get("revoked_at")!=null||!((java.time.OffsetDateTime)rows.get(0).get("expires_at")).isAfter(java.time.OffsetDateTime.now()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Refresh token expired or revoked");
  UUID user=(UUID)rows.get(0).get("user_id");jdbc.update("update refresh_tokens set revoked_at=now() where id=?",rows.get(0).get("id"));
  var u=jdbc.queryForMap("select email,participant_id,enabled,mfa_enabled from users where id=?",user);
  if(!Boolean.TRUE.equals(u.get("enabled")))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account disabled");
  return tokenResponse(user,(String)u.get("email"),u.get("participant_id"),roles(user));
 }
 @PostMapping("/auth/logout") @Transactional @PreAuthorize("isAuthenticated()")
 public Map<String,Object> logout(@RequestHeader(value="Authorization",required=false)String authorization,@RequestBody(required=false)RefreshRequest body,@AuthenticationPrincipal TradingPrincipal actor){
  if(authorization!=null&&authorization.startsWith("Bearer "))try{String raw=authorization.substring(7);jdbc.update("insert into revoked_access_tokens(token_id,user_id,expires_at) values (?,?,?) on conflict do nothing",jwt.tokenId(raw),actor.userId(),java.time.OffsetDateTime.ofInstant(jwt.expiresAt(raw),java.time.ZoneOffset.UTC));}catch(Exception ignored){}
  if(body!=null&&body.refreshToken()!=null)jdbc.update("update refresh_tokens set revoked_at=now() where token_hash=? and user_id=? and revoked_at is null",sha(body.refreshToken()),actor.userId());
  return Map.of("loggedOut",true);
 }
 @PostMapping("/auth/mfa/setup") @Transactional @PreAuthorize("isAuthenticated()")
 public Map<String,String> setupMfa(@AuthenticationPrincipal TradingPrincipal actor){
  String secret=totp.newSecret();jdbc.update("update users set mfa_secret_encrypted=?,mfa_enabled=false where id=?",totp.encrypt(secret),actor.userId());
  return Map.of("secret",secret,"otpauthUri",totp.provisioningUri(actor.email(),secret,"CommodityCX"));
 }
 @PostMapping("/auth/mfa/enable") @Transactional @PreAuthorize("isAuthenticated()")
 public Map<String,Object> enableMfa(@RequestBody Map<String,String> in,@AuthenticationPrincipal TradingPrincipal actor){
  String encrypted=jdbc.queryForObject("select mfa_secret_encrypted from users where id=?",String.class,actor.userId());
  if(encrypted==null||!totp.verify(totp.decrypt(encrypted),in.get("code")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"MFA code invalid");
  jdbc.update("update users set mfa_enabled=true where id=?",actor.userId());audit(actor.userId(),actor.participantId(),"MFA_ENABLED","USER",actor.userId().toString());
  return Map.of("mfaEnabled",true);
 }
 @PostMapping("/auth/mfa/disable") @Transactional @PreAuthorize("isAuthenticated()")
 public Map<String,Object> disableMfa(@RequestBody Map<String,String> in,@AuthenticationPrincipal TradingPrincipal actor){
  String encrypted=jdbc.queryForObject("select mfa_secret_encrypted from users where id=?",String.class,actor.userId());
  if(encrypted==null||!totp.verify(totp.decrypt(encrypted),in.get("code")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"MFA code invalid");
  jdbc.update("update users set mfa_enabled=false,mfa_secret_encrypted=null where id=?",actor.userId());return Map.of("mfaEnabled",false);
 }
 @PostMapping("/admin/participants/{participantId}/activate") @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE')") @Transactional
 public Map<String,Object> activate(@PathVariable UUID participantId,@AuthenticationPrincipal TradingPrincipal actor){
  int changed=jdbc.update("update participants set status='ACTIVE',updated_at=now() where id=? and status='PENDING'",participantId);
  if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Pending participant not found");
  jdbc.update("update users set enabled=true where participant_id=?",participantId);jdbc.update("insert into risk_limits(participant_id) values (?) on conflict do nothing",participantId);
  audit(actor.userId(),participantId,"PARTICIPANT_ACTIVATED","PARTICIPANT",participantId.toString());return Map.of("participantId",participantId,"status","ACTIVE");
 }
 @GetMapping("/admin/participants") @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE')")
 public List<Map<String,Object>> participants(){return jdbc.queryForList("select p.*,count(u.id) user_count from participants p left join users u on u.participant_id=p.id group by p.id order by p.created_at desc");}
 @PutMapping("/admin/users/{userId}/roles") @PreAuthorize("hasRole('ADMIN')") @Transactional
 public Map<String,Object> assignRoles(@PathVariable UUID userId,@RequestBody Map<String,List<String>> body,@AuthenticationPrincipal TradingPrincipal actor){
  List<String> names=body.getOrDefault("roles",List.of());if(names.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"At least one role is required");
  jdbc.update("delete from user_roles where user_id=?",userId);
  for(String role:names){int n=jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where name=?",userId,role.toUpperCase(Locale.ROOT));if(n==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown role: "+role);}
  audit(actor.userId(),null,"USER_ROLES_UPDATED","USER",userId.toString());return Map.of("userId",userId,"roles",names);
 }
 private Map<String,Object> tokenResponse(UUID user,String email,Object participant,List<String> roles){
  String raw=newRefresh();UUID id=UUID.randomUUID();jdbc.update("insert into refresh_tokens(id,user_id,token_hash,expires_at) values (?,?,?,?)",id,user,sha(raw),java.time.OffsetDateTime.now().plusDays(7));
  return Map.of("accessToken",jwt.issue(user,email,roles),"refreshToken",raw,"tokenType","Bearer","expiresInSeconds",600,"user",Map.of("id",user,"email",email,"participantId",participant==null?"":participant,"roles",roles));
 }
 private List<String> roles(UUID id){return jdbc.query("select r.name from roles r join user_roles ur on ur.role_id=r.id where ur.user_id=?",(rs,n)->rs.getString(1),id);}
 private static String newRefresh(){byte[] b=new byte[48];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 private static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private void audit(UUID actor,UUID participant,String type,String entity,String id){jdbc.update("insert into audit_events(actor_user_id,participant_id,event_type,entity_type,entity_id) values (?,?,?,?,?)",actor,participant,type,entity,id);jdbc.update("insert into event_outbox(aggregate_type,aggregate_id,event_type,payload) values (?,?,?,jsonb_build_object('entityType',?,'entityId',?))",entity,id,type,entity,id);}
}
