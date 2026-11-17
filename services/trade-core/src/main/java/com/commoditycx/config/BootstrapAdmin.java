package com.commoditycx.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;

@Configuration
public class BootstrapAdmin {
    @Bean CommandLineRunner bootstrapAdmin(JdbcTemplate db, PasswordEncoder encoder,
        @Value("${commoditycx.bootstrap.admin-email:}") String email,
        @Value("${commoditycx.bootstrap.admin-password:}") String password) {
        return args -> {
            if(email.isBlank() && password.isBlank()) return;
            if(email.isBlank() || password.length()<16) throw new IllegalStateException("Set both BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD (minimum 16 characters)");
            Integer exists=db.queryForObject("select count(*) from users where email=?",Integer.class,email.toLowerCase());
            if(exists!=null && exists>0) return;
            UUID participant=db.queryForObject("insert into participants(code,legal_name,country_code,status) values ('PLATFORM-ADMIN','CommodityCX Platform Administration','IN','ACTIVE') on conflict(code) do update set status='ACTIVE' returning id",UUID.class);
            UUID user=db.queryForObject("insert into users(participant_id,email,full_name,password_hash,enabled) values (?,?,?,?,true) returning id",UUID.class,participant,email.toLowerCase(),"Platform Administrator",encoder.encode(password));
            db.update("insert into user_roles(user_id,role_id) select ?,id from roles where name='ADMIN'",user);
            db.update("insert into risk_limits(participant_id) values (?) on conflict do nothing",participant);
        };
    }
}
