package com.bts.viewplustest.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {@UniqueConstraint(name = "uk_users_email", columnNames = "email"), @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname")})
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 254) private String email;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, length = 30) private String nickname;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    protected User() {}
    public User(String email, String passwordHash, String nickname) { this.email = email; this.passwordHash = passwordHash; this.nickname = nickname; }
    public Long getId() { return id; } public String getEmail() { return email; } public String getPasswordHash() { return passwordHash; } public String getNickname() { return nickname; }
    public void changeNickname(String value) { nickname = value; }
    public void changePasswordHash(String value) { passwordHash = value; }
}
