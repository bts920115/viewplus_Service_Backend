package com.bts.viewplustest.auth;

import com.bts.viewplustest.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        String email = request.email().trim().toLowerCase();
        String nickname = request.nickname().trim();
        if (users.existsByEmail(email)) throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        if (users.existsByNickname(nickname)) throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        User user = users.save(new User(email, passwords.encode(request.password()), nickname));
        return response(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByEmail(request.email().trim().toLowerCase()).filter(u -> passwords.matches(request.password(), u.getPasswordHash())).orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));
        return response(user);
    }

    private AuthResponse response(User u) {
        return new AuthResponse(jwt.createToken(u.getId(), u.getEmail()), u.getId(), u.getEmail(), u.getNickname());
    }

    public record SignupRequest(@NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @Size(min = 8, max = 72) String password,
                                @NotBlank @Size(min = 2, max = 30) String nickname) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record AuthResponse(String accessToken, Long userId, String email, String nickname) {
    }
}
