package com.example.choosecourse.config;

import com.example.choosecourse.auth.CaptchaService;
import com.example.choosecourse.auth.LoginAttemptService;
import com.example.choosecourse.auth.LoginGuardFilter;
import com.example.choosecourse.auth.RoleBasedSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RoleBasedSuccessHandler successHandler,
                                                   CaptchaService captchaService,
                                                   LoginAttemptService loginAttemptService) throws Exception {
        LoginGuardFilter loginGuardFilter = new LoginGuardFilter(captchaService, loginAttemptService);

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login", "/forgot-password", "/captcha",
                                "/help", "/about",
                                "/css/**", "/js/**", "/image/**", "/video/**", "/live2d/**",
                                "/error", "/actuator/health"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/student/**").hasRole("STUDENT")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("Id")
                        .passwordParameter("Pwd")
                        .successHandler(successHandler)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .addFilterBefore(loginGuardFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
