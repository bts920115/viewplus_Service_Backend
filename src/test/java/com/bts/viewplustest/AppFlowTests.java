package com.bts.viewplustest;
import com.bts.viewplustest.auth.AuthController;
import com.bts.viewplustest.friend.FriendController;
import com.bts.viewplustest.user.ProfileController;
import com.bts.viewplustest.user.UserRepository;
import com.bts.viewplustest.room.LiveRoomController;
import com.bts.viewplustest.room.RoomVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Transactional
class AppFlowTests {
 @Autowired AuthController auth; @Autowired FriendController friends; @Autowired ProfileController profiles;
 @Autowired UserRepository users; @Autowired LiveRoomController rooms;
 @Test void signupFriendAcceptanceAndLiveListing() {
   String suffix=java.util.UUID.randomUUID().toString().substring(0,8);
   var first=auth.signup(new AuthController.SignupRequest("a"+suffix+"@example.com","ExamplePass123!","a"+suffix));
   var second=auth.signup(new AuthController.SignupRequest("b"+suffix+"@example.com","ExamplePass123!","b"+suffix));
   var a=new UsernamePasswordAuthenticationToken(first.userId(),null,java.util.List.of());
   var b=new UsernamePasswordAuthenticationToken(second.userId(),null,java.util.List.of());
   var request=friends.request(a,new FriendController.EmailRequest(second.email()));
   assertTrue(friends.list(a).isEmpty());
   assertEquals(1,friends.requests(b).size());
   friends.accept(b,request.relationId());
   assertEquals(second.userId(),friends.list(a).get(0).userId());
   var live=rooms.create(a,new LiveRoomController.CreateRoomRequest("Integration live",RoomVisibility.PUBLIC));
   assertTrue(rooms.list().stream().anyMatch(r->r.id().equals(live.id())));
   rooms.end(a,live.id());
   assertFalse(rooms.list().stream().anyMatch(r->r.id().equals(live.id())));
 }
 @Test void passwordChangeRequiresCurrentPassword() {
   String suffix=java.util.UUID.randomUUID().toString().substring(0,8);
   var user=auth.signup(new AuthController.SignupRequest(suffix+"@example.com","ExamplePass123!","u"+suffix));
   var principal=new UsernamePasswordAuthenticationToken(user.userId(),null,java.util.List.of());
   assertThrows(IllegalArgumentException.class,()->profiles.update(principal,new ProfileController.Update(null,"NewPassword123!","incorrect")));
   profiles.update(principal,new ProfileController.Update(null,"NewPassword123!","ExamplePass123!"));
   assertNotNull(auth.login(new AuthController.LoginRequest(user.email(),"NewPassword123!")).accessToken());
 }
}

