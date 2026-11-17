package com.commoditycx.api;

import com.commoditycx.security.JwtService;
import com.commoditycx.security.TradingPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final JdbcTemplate jdbc; private final PasswordEncoder passwords; private final JwtService jwt;
    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwords, JwtService jwt) {
        this.jdbc=jdbc; this.passwords=passwords; this.jwt=jwt;
    }
    public record RegisterRequest(@NotBlank @Size(max=160) String fullName, @Email @NotBlank String email,
        @Size(min=12,max=72) String password, @NotBlank String legalName, @Size(min=2,max=2) String countryCode) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> register(@Valid @RequestBody RegisterRequest in) {
        String code="P-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        UUID participant=jdbc.queryForObject("insert into participants(code,legal_name,country_code) values (?,?,?) returning id",
            UUID.class,code,in.legalName(),in.countryCode().toUpperCase(Locale.ROOT));
        UUID user=jdbc.queryForObject("insert into users(participant_id,email,full_name,password_hash) values (?,?,?,?) returning id",
            UUID.class,participant,in.email().toLowerCase(Locale.ROOT),in.fullName(),passwords.encode(in.password()));
        jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where name='TRADER'",user);
        audit(user,participant,"PARTICIPANT_REGISTERED","PARTICIPANT",participant.toString());
        return Map.of("participantId",participant,"participantCode",code,"userId",user,"status","PENDING_APPROVAL",
            "message","Registration received. Compliance/admin approval is required before login.");
    }
    @PostMapping("/auth/login")
    public Map<String,Object> login(@Valid @RequestBody LoginRequest in) {
        var rows=jdbc.queryForList("select id,password_hash,enabled,participant_id,email from users where email=?",in.email().toLowerCase(Locale.ROOT));
        if(rows.isEmpty() || !passwords.matches(in.password(),(String)rows.get(0).get("password_hash")))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        var u=rows.get(0);
        if(!Boolean.TRUE.equals(u.get("enabled"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account is pending approval or disabled");
        UUID id=(UUID)u.get("id");
        List<String> roles=jdbc.query("select r.name from roles r join user_roles ur on ur.role_id=r.id where ur.user_id=?",
            (rs,n)->rs.getString(1),id);
        jdbc.update("update users set last_login_at=now() where id=?",id);
        return Map.of("accessToken",jwt.issue(id,(String)u.get("email"),roles),"tokenType","Bearer",
            "user",Map.of("id",id,"email",u.get("email"),"participantId",u.get("participant_id"),"roles",roles));
    }
    @PostMapping("/admin/participants/{participantId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE')") @Transactional
    public Map<String,Object> activate(@PathVariable UUID participantId,@org.springframework.security.core.annotation.AuthenticationPrincipal TradingPrincipal actor) {
        int changed=jdbc.update("update participants set status='ACTIVE',updated_at=now() where id=? and status='PENDING'",participantId);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Pending participant not found");
        jdbc.update("update users set enabled=true where participant_id=?",participantId);
        jdbc.update("insert into risk_limits(participant_id) values (?) on conflict do nothing",participantId);
        audit(actor.userId(),participantId,"PARTICIPANT_ACTIVATED","PARTICIPANT",participantId.toString());
        return Map.of("participantId",participantId,"status","ACTIVE");
    }
    @GetMapping("/admin/participants") @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE')")
    public List<Map<String,Object>> participants() {
        return jdbc.queryForList("select p.*,count(u.id) user_count from participants p left join users u on u.participant_id=p.id group by p.id order by p.created_at desc");
    }
    @PutMapping("/admin/users/{userId}/roles") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Map<String,Object> assignRoles(@PathVariable UUID userId,@RequestBody Map<String,List<String>> body,
        @org.springframework.security.core.annotation.AuthenticationPrincipal TradingPrincipal actor) {
        List<String> names=body.getOrDefault("roles",List.of());
        if(names.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"At least one role is required");
        jdbc.update("delete from user_roles where user_id=?",userId);
        for(String role:names) {
            int n=jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where name=?",userId,role.toUpperCase(Locale.ROOT));
            if(n==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown role: "+role);
        }
        audit(actor.userId(),null,"USER_ROLES_UPDATED","USER",userId.toString());
        return Map.of("userId",userId,"roles",names);
    }
    private void audit(UUID actor,UUID participant,String type,String entity,String id) {
        jdbc.update("insert into audit_events(actor_user_id,participant_id,event_type,entity_type,entity_id) values (?,?,?,?,?)",actor,participant,type,entity,id);
    }
}