package com.commoditycx.security;
import com.commoditycx.security.TradingPrincipal;
import org.springframework.core.convert.converter.Converter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class OidcTradingAuthenticationConverter implements Converter<Jwt,AbstractAuthenticationToken> {
 private final JdbcTemplate db;
 public OidcTradingAuthenticationConverter(JdbcTemplate db){this.db=db;}
 @Override public AbstractAuthenticationToken convert(Jwt jwt){
  String email=jwt.getClaimAsString("email");
  if(email==null||!Boolean.TRUE.equals(jwt.getClaim("email_verified")))throw new BadCredentialsException("OIDC token must contain a verified email claim");
  List<Map<String,Object>> users=db.queryForList("select id,email,participant_id from users where lower(email)=lower(?) and enabled=true",email);
  if(users.isEmpty())throw new BadCredentialsException("OIDC identity is not linked to an active CommodityCX user");
  Map<String,Object> user=users.get(0);UUID id=(UUID)user.get("id");
  List<SimpleGrantedAuthority> authorities=db.query("select r.name from roles r join user_roles ur on ur.role_id=r.id where ur.user_id=?",(rs,n)->new SimpleGrantedAuthority("ROLE_"+rs.getString(1)),id);
  TradingPrincipal principal=new TradingPrincipal(id,(String)user.get("email"),user.get("participant_id")==null?null:(UUID)user.get("participant_id"));
  return new UsernamePasswordAuthenticationToken(principal,jwt,authorities);
 }
}