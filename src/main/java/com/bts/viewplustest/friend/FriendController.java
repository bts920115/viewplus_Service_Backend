package com.bts.viewplustest.friend;
import com.bts.viewplustest.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
@RestController @RequestMapping("/api/friends") @Transactional
public class FriendController {
 private final UserFriendRepository friends; private final UserRepository users;
 public FriendController(UserFriendRepository f,UserRepository u){friends=f;users=u;}
 private List<UserFriend> relations(Long me,FriendStatus status){return friends.findByStatusAndRequesterIdOrStatusAndAddresseeId(status,me,status,me);}
 private FriendResponse response(UserFriend f,Long me){User other=f.getRequester().getId().equals(me)?f.getAddressee():f.getRequester();return new FriendResponse(f.getId(),other.getId(),other.getNickname());}
 @GetMapping public List<FriendResponse> list(Authentication a){Long me=(Long)a.getPrincipal();return relations(me,FriendStatus.ACCEPTED).stream().map(f->response(f,me)).toList();}
 @GetMapping("/requests") public List<FriendResponse> requests(Authentication a){Long me=(Long)a.getPrincipal();return relations(me,FriendStatus.PENDING).stream().filter(f->f.getAddressee().getId().equals(me)).map(f->response(f,me)).toList();}
 @PostMapping("/by-email") public FriendResponse request(Authentication a,@Valid @RequestBody EmailRequest q){
 Long me=(Long)a.getPrincipal();User target=users.findByEmail(q.email().trim().toLowerCase(Locale.ROOT)).orElseThrow(()->new IllegalArgumentException("사용자를 찾을 수 없습니다."));
 if(me.equals(target.getId()))throw new IllegalArgumentException("자신에게 요청할 수 없습니다.");
 for(FriendStatus status:FriendStatus.values())if(relations(me,status).stream().anyMatch(f->response(f,me).userId().equals(target.getId())))throw new IllegalArgumentException("이미 관계 또는 요청이 있습니다.");
 return response(friends.save(new UserFriend(users.findById(me).orElseThrow(),target)),me);
 }
 @PostMapping("/{id}/accept") public void accept(Authentication a,@PathVariable Long id){UserFriend f=friends.findByIdAndAddresseeId(id,(Long)a.getPrincipal()).orElseThrow(()->new IllegalArgumentException("요청을 찾을 수 없습니다."));if(f.getStatus()!=FriendStatus.PENDING)throw new IllegalArgumentException("대기 중인 요청이 아닙니다.");f.accept();}
 public record EmailRequest(@NotBlank @Email String email){}
 public record FriendResponse(Long relationId,Long userId,String nickname){}
}
