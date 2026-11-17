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
    private final JwtService jwt;
    private final JdbcTemplate jdbc;
    public JwtAuthenticationFilter(JwtService jwt, JdbcTemplate jdbc) { this.jwt = jwt; this.jdbc = jdbc; }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var userId = jwt.subject(header.substring(7));
                var user = jdbc.queryForMap("select id,email,participant_id from users where id=? and enabled=true", userId);
                List<SimpleGrantedAuthority> authorities = jdbc.query(
                    "select r.name from roles r join user_roles ur on ur.role_id=r.id where ur.user_id=?",
                    (rs, n) -> new SimpleGrantedAuthority("ROLE_" + rs.getString(1)), userId);
                var principal = new TradingPrincipal(userId, (String) user.get("email"),
                        user.get("participant_id") == null ? null : (java.util.UUID) user.get("participant_id"));
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
            } catch (Exception ignored) { SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(req, res);
    }
}
