package com.bts.viewplustest;
import com.bts.viewplustest.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSessionTests {
 @Autowired Environment env; @Autowired UserRepository users; @Autowired PasswordEncoder passwords;
 final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
 String base(){return "http://localhost:"+env.getProperty("local.server.port");}
 HttpResponse<String> get(String path)throws Exception{return client.send(HttpRequest.newBuilder(URI.create(base()+path)).build(),HttpResponse.BodyHandlers.ofString());}
 HttpResponse<String> post(String path,String body,String csrf)throws Exception{
  var b=HttpRequest.newBuilder(URI.create(base()+path)).header("Content-Type","application/json");
  if(csrf!=null)b.header("X-CSRF-TOKEN",csrf);
  return client.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 String csrf()throws Exception{return new tools.jackson.databind.ObjectMapper().readTree(get("/web/auth/csrf").body()).path("token").asText();}
 @Test void cookieLoginSurvivesRequestsAndLogoutRevokesSession()throws Exception{
  String unique=java.util.UUID.randomUUID().toString().substring(0,8);
  User u=users.save(new User(unique+"@example.com",passwords.encode("SessionPass123!"),"web"+unique));
  try{
   String body="{\"email\":\""+u.getEmail()+"\",\"password\":\"SessionPass123!\"}";
   assertEquals(403,post("/web/auth/login",body,null).statusCode());
   assertEquals(200,post("/web/auth/login",body,csrf()).statusCode());
   assertEquals(200,get("/api/me").statusCode());
   assertEquals(200,get("/api/rooms").statusCode());
   assertEquals(200,get("/api/friends").statusCode());
   assertEquals(200,get("/api/me").statusCode());
   assertEquals(403,post("/web/auth/logout","{}",null).statusCode());
   assertEquals(200,post("/web/auth/logout","{}",csrf()).statusCode());
   assertEquals(401,get("/api/me").statusCode());
   assertEquals(200,get("/viewer").statusCode());
  }finally{users.deleteById(u.getId());}
 }
}
