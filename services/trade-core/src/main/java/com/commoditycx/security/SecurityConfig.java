package com.commoditycx.security;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.List;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean CorsConfigurationSource corsConfigurationSource(@Value("${FRONTEND_ORIGIN:http://localhost:4200}")String origin){
  CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(List.of(origin));c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Authorization","Content-Type","Accept","Idempotency-Key"));c.setAllowCredentials(false);
  UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;
 }
 @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("'${commoditycx.security.oidc-issuer-uri:}' != ''")
 JwtDecoder oidcJwtDecoder(@Value("${commoditycx.security.oidc-issuer-uri}")String issuer,@Value("${OIDC_AUDIENCE:commoditycx-api}")String audience){
  NimbusJwtDecoder decoder=NimbusJwtDecoder.withIssuerLocation(issuer).build();
  OAuth2TokenValidator<Jwt> audienceCheck=token->token.getAudience().contains(audience)?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Required audience is missing",null));
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),audienceCheck));return decoder;
 }
 @Bean BearerTokenResolver oidcTokenResolver(){
  DefaultBearerTokenResolver delegate=new DefaultBearerTokenResolver();
  return request->{String value=request.getHeader("Authorization");if(value!=null&&value.startsWith("Bearer ")){
   try{String[] parts=value.substring(7).split("\\.");if(parts.length==3){String payload=new String(Base64.getUrlDecoder().decode(parts[1]),java.nio.charset.StandardCharsets.UTF_8);if(payload.contains("auth_source") && payload.contains("commoditycx"))return null;}}catch(Exception ignored){}
  }return delegate.resolve(request);};
 }
 @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwt,ObjectProvider<JwtDecoder> decoder,OidcTradingAuthenticationConverter converter)throws Exception{
  http.cors(c->{}).csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/**","/actuator/health").permitAll().anyRequest().authenticated())
   .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class);
  JwtDecoder oidc=decoder.getIfAvailable();
  if(oidc!=null)http.oauth2ResourceServer(o->o.bearerTokenResolver(oidcTokenResolver()).jwt(j->j.decoder(oidc).jwtAuthenticationConverter(converter)));
  return http.build();
 }
}
