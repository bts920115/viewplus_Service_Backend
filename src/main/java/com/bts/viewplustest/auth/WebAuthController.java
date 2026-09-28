package com.bts.viewplustest.auth;

import com.bts.viewplustest.SignalingHandler;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/web/auth")
public class WebAuthController {
    private final AuthController auth;
    private final SignalingHandler signaling;
    public WebAuthController(AuthController auth, SignalingHandler signaling) { this.auth=auth; this.signaling=signaling; }
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("token",token.getToken(),"headerName",token.getHeaderName());
    }
    @PostMapping("/login") public Map<String,Object> login(@Valid @RequestBody AuthController.LoginRequest body,
            HttpServletRequest request,HttpServletResponse response) {
        return establish(auth.login(body),request,response);
    }
    @PostMapping("/signup") public Map<String,Object> signup(@Valid @RequestBody AuthController.SignupRequest body,
            HttpServletRequest request,HttpServletResponse response) {
        return establish(auth.signup(body),request,response);
    }
    private Map<String,Object> establish(AuthController.AuthResponse user,HttpServletRequest request,HttpServletResponse response) {
        request.getSession(); request.changeSessionId();
        request.getSession().setMaxInactiveInterval(-1);
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.userId(),null,List.of()));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
        return Map.of("id",user.userId(),"nickname",user.nickname(),"email",user.email());
    }
    @PostMapping("/logout") public Map<String,Boolean> logout(HttpServletRequest request) {
        var session=request.getSession(false);
        if(session!=null){signaling.closeBrowserSession(session.getId());session.invalidate();}
        SecurityContextHolder.clearContext();
        return Map.of("loggedOut",true);
    }
}
