package com.bts.viewplustest;
import com.bts.viewplustest.auth.JwtService;
import com.bts.viewplustest.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpAuthenticationTests {
 @Autowired Environment environment;
 @Autowired JwtService jwt;
 @Autowired UserRepository users;
 @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
 private HttpResponse<String> request(String path,String token,String body) throws Exception {
   var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+environment.getProperty("local.server.port")+path));
   if(token!=null)builder.header("Authorization","Bearer "+token);
   if(body!=null)builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
   return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void invalidSignupIsValidationErrorNotForbidden() throws Exception {
   assertEquals(400,request("/api/auth/signup",null,"{}").statusCode());
 }
 @Test void issuedTokenCanAccessEveryHomeTab() throws Exception {
   String unique=java.util.UUID.randomUUID().toString().substring(0,8);
   User user=users.save(new User(unique+"@example.com",passwords.encode("HttpTestPass123!"),"http"+unique));
   try {
     var login=request("/api/auth/login",null,"{\"email\":\""+user.getEmail()+"\",\"password\":\"HttpTestPass123!\"}");
     assertEquals(200,login.statusCode(),login.body());
     String token=new tools.jackson.databind.ObjectMapper().readTree(login.body()).path("accessToken").asText();
     for(String path:java.util.List.of("/api/me","/api/rooms","/api/friends","/api/friends/requests","/api/video-links")){
       var response=request(path,token,null);
       assertEquals(200,response.statusCode(),path+" "+response.body());
     }
   } finally {users.deleteById(user.getId());}
 }
 @Test void missingAndInvalidTokensReturnUnauthorized() throws Exception {
   assertEquals(401,request("/api/me",null,null).statusCode());
   assertEquals(401,request("/api/me","invalid-token",null).statusCode());
 }
}
