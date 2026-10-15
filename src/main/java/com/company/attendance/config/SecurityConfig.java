package com.company.attendance.config;

import com.company.attendance.repository.AppUserRepository;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.Path;

@Configuration
public class SecurityConfig implements WebMvcConfigurer {
    private final AppProperties props;
    public SecurityConfig(AppProperties props) { this.props = props; }

    @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService users(AppUserRepository repo) {
        return name -> repo.findByUsername(name)
            .map(u -> User.withUsername(u.getUsername()).password(u.getPasswordHash())
                          .roles(u.getRole().name()).disabled(!u.isEnabled()).build())
            .orElseThrow(() -> new UsernameNotFoundException(name));
    }

    @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.ignoringRequestMatchers("/iclock/**", "/api/**"))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/iclock/**", "/js/**").permitAll()   // ZKTeco readers push here (no login)
                .requestMatchers("/employees/**", "/shifts/**", "/simulator/**", "/api/**").hasRole("ADMIN")
                .requestMatchers("/logs/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers("/", "/dashboard/**", "/photos/**").hasAnyRole("ADMIN", "HR", "GUARD")
                .anyRequest().authenticated())
            .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/dashboard", true).permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout").permitAll())
            .httpBasic(Customizer.withDefaults());   // lets scripts call /api/scans with basic auth
        return http.build();
    }

    /** Serve employee photos from the data dir. */
    @Override public void addResourceHandlers(ResourceHandlerRegistry r) {
        r.addResourceHandler("/photos/**")
         .addResourceLocations(Path.of(props.getDataDir(), "photos").toUri().toString());
    }
}
