package com.commoditycx.security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtService jwt;private final JdbcTemplate jdbc;
 public JwtAuthenticationFilter(JwtService jwt,JdbcTemplate jdbc){this.jwt=jwt;this.jdbc=jdbc;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  if(SecurityContextHolder.getContext().getAuthentication()==null){
   String header=req.getHeader("Authorization");
   if(header!=null&&header.startsWith("Bearer "))try{
    String token=header.substring(7);var claims=jwt.claims(token);var userId=jwt.subject(token);
    Integer revoked=jdbc.queryForObject("select count(*) from revoked_access_tokens where token_id=? and expires_at>now()",Integer.class,jwt.tokenId(token));
    if(revoked!=null&&revoked==0){
     var user=jdbc.queryForMap("select id,email,participant_id from users where id=? and enabled=true",userId);
     List<SimpleGrantedAuthority> authorities=jdbc.query("select r.name from roles r join user_roles ur on ur.role_id=r.id where ur.user_id=?",(rs,n)->new SimpleGrantedAuthority("ROLE_"+rs.getString(1)),userId);
     var principal=new TradingPrincipal(userId,(String)user.get("email"),user.get("participant_id")==null?null:(java.util.UUID)user.get("participant_id"));
     SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,authorities));
    }
   }catch(Exception ignored){SecurityContextHolder.clearContext();}
  }
  chain.doFilter(req,res);
 }
}
