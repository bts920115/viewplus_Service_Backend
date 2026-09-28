(() => {
'use strict';
const $=id=>document.getElementById(id);
let signup=false, csrf, me, tab='live', generation=0, socket, peer, publisher, pending=[];
const status=text=>$('status').textContent=text;
const video=$('video'), stage=video.parentElement;
function syncVideoRatio(){
 if(video.videoWidth>0&&video.videoHeight>0)stage.style.setProperty('--video-ratio',String(video.videoWidth/video.videoHeight));
}
video.addEventListener('loadedmetadata',syncVideoRatio);
video.addEventListener('resize',syncVideoRatio);
async function token(){csrf=await fetch('/web/auth/csrf',{credentials:'same-origin'}).then(r=>r.json());}
async function api(path,method='GET',body){
 if(method!=='GET'&&!csrf)await token();
 const headers={'Content-Type':'application/json'};
 if(method!=='GET')headers[csrf.headerName]=csrf.token;
 const response=await fetch(path,{method,credentials:'same-origin',headers,body:body?JSON.stringify(body):undefined});
 const data=await response.json().catch(()=>({}));
 if(response.status===401){showAuth();throw Error('로그인이 만료되었습니다. 다시 로그인하세요.');}
 if(!response.ok)throw Error(data.message||'요청을 처리할 수 없습니다.');
 return data;
}
function showAuth(){closeLive();me=null;generation++;$('app').hidden=true;$('auth').hidden=false;$('logout').hidden=true;$('account').textContent='';}
function showApp(){ $('auth').hidden=true;$('app').hidden=false;$('logout').hidden=false;$('account').textContent=me.nickname;loadList();}
function closeLive(){if(socket){const old=socket;socket=null;old.onclose=null;old.onmessage=null;old.close();}if(peer){peer.close();peer=null;}$('video').srcObject=null;stage.style.removeProperty('--video-ratio');pending=[];publisher=null;}
function row(title,detail,action,live=false){
 const element=document.createElement(action?'button':'div');element.className='row';
 const icon=document.createElement('span');icon.className='play';icon.textContent=live?'▶':'○';
 const text=document.createElement('div'),heading=document.createElement('strong'),meta=document.createElement('small');
 heading.textContent=title;meta.textContent=detail;text.append(heading,meta);element.append(icon,text);
 if(action)element.onclick=action;$('list').append(element);
}
async function loadList(){
 const current=++generation;closeLive();$('watch').hidden=true;$('browse').hidden=false;$('app-error').textContent='';
 $('list').replaceChildren();$('list-title').textContent=tab==='live'?'지금 라이브':'친구';
 $('friend-form').hidden=tab!=='friends';$('live-tab').classList.toggle('active',tab==='live');$('friend-tab').classList.toggle('active',tab==='friends');
 try{
 const rooms=await api('/api/rooms');if(current!==generation)return;
 if(tab==='live'){
  rooms.forEach(room=>row(room.title,room.hostNickname+' · '+new Date(room.startedAt).toLocaleString('ko-KR'),()=>watch(room),true));
  if(!rooms.length)row('진행 중인 방송이 없습니다.','새로고침하여 다시 확인해 주세요.');
 }else{
  const [friends,requests]=await Promise.all([api('/api/friends'),api('/api/friends/requests')]);if(current!==generation)return;
  requests.forEach(f=>row(f.nickname,'친구 요청 · 눌러서 수락',async()=>{try{await api('/api/friends/'+f.relationId+'/accept','POST',{});loadList();}catch(e){$('app-error').textContent=e.message;}}));
  friends.forEach(f=>{const live=rooms.find(r=>r.hostUserId===f.userId);row(f.nickname,live?'LIVE · '+live.title:'라이브 중이 아님',live?()=>watch(live):null,!!live);});
  if(!friends.length&&!requests.length)row('친구 목록이 비어 있습니다.','이메일로 친구를 추가해 보세요.');
 }
 }catch(e){if(current===generation)$('app-error').textContent=e.message;}
}
function addChat(sender,text){const line=document.createElement('p');line.textContent=sender+'  '+text;$('chat-log').append(line);while($('chat-log').children.length>200)$('chat-log').firstChild.remove();$('chat-log').scrollTop=$('chat-log').scrollHeight;}
async function watch(room){
 const current=++generation;closeLive();$('browse').hidden=true;$('watch').hidden=false;$('room-title').textContent=room.title;$('chat-log').replaceChildren();status('방에 연결하는 중입니다.');
 try{const messages=await api('/api/rooms/'+room.id+'/messages');if(current!==generation)return;messages.reverse().forEach(m=>addChat(m.senderNickname,m.content));}catch(e){if(current!==generation)return;$('app-error').textContent=e.message;}
 if(!me)return;
 const ws=new WebSocket((location.protocol==='https:'?'wss:':'ws:')+'//'+location.host+'/ws');socket=ws;
 ws.onopen=()=>ws.send(JSON.stringify({type:'join',roomId:String(room.id),role:'viewer'}));
 ws.onclose=()=>{if(socket===ws){status('서버 연결이 종료되었습니다. 목록에서 다시 입장하세요.');if(peer){peer.close();peer=null;}}};
 ws.onerror=()=>status('연결할 수 없습니다. 로그인 상태와 서버를 확인하세요.');
 let chain=Promise.resolve();
 ws.onmessage=event=>{chain=chain.then(async()=>{
 if(socket!==ws)return;const message=JSON.parse(event.data);
 switch(message.type){
 case 'waiting':status('방송자가 입장하기를 기다립니다.');break;
 case 'peer-ready':
  if(peer)peer.close();pending=[];publisher=message.peerId;peer=new RTCPeerConnection({iceServers:message.iceServers||[]});
  peer.onicecandidate=e=>{if(e.candidate&&ws.readyState===1)ws.send(JSON.stringify({type:'candidate',targetId:publisher,candidate:e.candidate.candidate,sdpMid:e.candidate.sdpMid,sdpMLineIndex:e.candidate.sdpMLineIndex}));};
  peer.ontrack=e=>{$('video').srcObject=e.streams[0]||new MediaStream([e.track]);$('video').play().catch(()=>status('영상의 재생 버튼을 눌러 시청하세요.'));};
  peer.onconnectionstatechange=()=>{if(!peer)return;status(({connected:'방송에 연결되었습니다.',connecting:'영상 연결 중입니다.',failed:'영상 연결 실패: 방송자와 다시 연결하거나 TURN 설정을 확인하세요.',disconnected:'영상 연결이 끊겼습니다.'})[peer.connectionState]||'영상 신호를 기다립니다.');};
  status('방송자의 영상 신호를 기다립니다.');break;
 case 'offer':
  if(!peer)throw Error('방송 연결 정보가 없습니다. 다시 입장하세요.');
  await peer.setRemoteDescription({type:'offer',sdp:message.sdp});
  for(const candidate of pending)await peer.addIceCandidate(candidate);pending=[];
  const answer=await peer.createAnswer();await peer.setLocalDescription(answer);
  ws.send(JSON.stringify({type:'answer',targetId:publisher,sdp:answer.sdp}));break;
 case 'candidate':
  const candidate={candidate:message.candidate,sdpMid:message.sdpMid,sdpMLineIndex:message.sdpMLineIndex};
  if(peer?.remoteDescription)await peer.addIceCandidate(candidate);else pending.push(candidate);break;
 case 'chat':addChat(message.senderNickname||'참여자',message.message);break;
 case 'peer-left':if(peer){peer.close();peer=null;}$('video').srcObject=null;status('방송이 종료되었습니다.');break;
 case 'error':status(message.message||'입장할 수 없습니다.');break;
 }
 }).catch(e=>status(e.message));};
}
$('auth-toggle').onclick=()=>{signup=!signup;$('auth-title').textContent=signup?'회원가입':'로그인';$('auth-submit').textContent=signup?'계정 만들기':'로그인';$('auth-toggle').textContent=signup?'로그인으로 돌아가기':'회원가입';$('nickname-field').hidden=!signup;$('nickname').required=signup;$('password').autocomplete=signup?'new-password':'current-password';$('auth-error').textContent='';};
$('auth-form').onsubmit=async e=>{e.preventDefault();$('auth-submit').disabled=true;$('auth-error').textContent='';try{me=await api('/web/auth/'+(signup?'signup':'login'),'POST',{email:$('email').value.trim(),password:$('password').value,...(signup?{nickname:$('nickname').value.trim()}:{})});$('password').value='';await token();showApp();}catch(e){$('auth-error').textContent=e.message;}finally{$('auth-submit').disabled=false;}};
$('logout').onclick=async()=>{try{await api('/web/auth/logout','POST',{});showAuth();csrf=null;await token();}catch(e){$('app-error').textContent=e.message;}};
$('live-tab').onclick=()=>{tab='live';loadList();};$('friend-tab').onclick=()=>{tab='friends';loadList();};$('refresh').onclick=loadList;$('leave').onclick=loadList;
$('friend-form').onsubmit=async e=>{e.preventDefault();try{await api('/api/friends/by-email','POST',{email:$('friend-email').value.trim()});$('friend-email').value='';$('app-error').textContent='친구 요청을 보냈습니다.';}catch(e){$('app-error').textContent=e.message;}};
$('chat-form').onsubmit=e=>{e.preventDefault();const text=$('chat-input').value.trim();if(!text)return;if(!socket||socket.readyState!==1){status('먼저 방송에 연결해 주세요.');return;}socket.send(JSON.stringify({type:'chat',message:text}));addChat(me.nickname,text);$('chat-input').value='';};
window.addEventListener('pagehide',closeLive);
(async()=>{try{await token();const response=await fetch('/api/me',{credentials:'same-origin'});if(response.ok){me=await response.json();showApp();}else if(response.status!==401)$('auth-error').textContent='서버 상태를 확인하세요.';}catch(e){$('auth-error').textContent='서버에 연결할 수 없습니다.';}})();
})();
