package com.example.demo.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.activerecord.Model;
import com.example.demo.config.WebSocketConfig;
import com.example.demo.entity.Chat;
import com.example.demo.entity.Friends;
import com.example.demo.entity.Users;
import com.example.demo.model.ChatMessage;
import com.example.demo.model.User;
import com.example.demo.service.IChatMessageService;
import com.example.demo.service.IFriendsService;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author ocly
 * @date 2018/2/2 15:42
 */
@RestController
public class ChatController {

  private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM-dd HH:mm");

  @Autowired
  IChatMessageService iChatMessageService;

  @Autowired
  IFriendsService iFriendsService;

  //Fake data from database
  private Set<String> all_users = new HashSet<String>(){{add("Tom");add("Kobe");add("James");}};
  private Set<String> online_users = new HashSet();
//  private List<ChatMessage> hist_messages = new ArrayList(){{
//      add(new ChatMessage("Kobe", "Hi James, I am Kobe!", "James", "05-02 05:41"));
//      add(new ChatMessage("James", "Hi Kobe, I am James!", "Kobe", "05-02 05:42"));
//  }};

  @Autowired
  private SimpMessagingTemplate template;

  @Autowired
  WebSocketConfig webSocketConfig;

  @GetMapping("/userlist")
  public JSONObject getUserlist(){
    JSONObject users = webSocketConfig.users;
    System.out.print(users);
    //users is an json object of all online user
    //key is session id (unique), value is name(Also id in DB)
    //Read all users(including offline users) from database

    for (Object online_u: users.values()){
      online_users.add(String.valueOf(online_u));
      if (all_users.contains(String.valueOf(online_u))){
            all_users.remove(String.valueOf(online_u));
        }
    }

    System.out.println("-----------------------Online Users:-----------------------");
    System.out.println(online_users);
    //Test code
//    Users kobe = new Users();
//    kobe.setName("Kobe");
//    List<Friends> friendsList =  iFriendsService.selectByUser(kobe);
//    System.out.println(friendsList);

    return users;
  }

  @GetMapping("/offline_userlist")
  public JSONObject getOfflineUserlist(){

    JSONObject off_line_user = new JSONObject();
    try {
      //添加
      int j = 1;
      System.out.println("fuckfuckfuckfuckfuck!!!!!!@@!!!!!!!!!!");
      System.out.println(all_users);
      Object[] all_users_arr = all_users.toArray();
      for(int i = 0; i < all_users_arr.length; i++){
        //假如该该user不在线即放入这个offline的JSONobject中
        if(!online_users.contains(String.valueOf(all_users_arr[i]))){
          //将offline放入object中
          off_line_user.put(String.valueOf(j),all_users_arr[i]);
          j++;
        }
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }


    online_users = new HashSet<>();

//    //list转json字符串
//    String string = JSON.toJSON(all_users).toString();
//    System.out.println(string);
//
//    //json字符串转listJson格式
//    JSONArray jsonArray = JSONObject.parseArray(string);
//
//    Iterator iter = jsonArray.entrySet().iterator();
//    while (iter.hasNext()) {
//      Map.Entry entry = (Map.Entry) iter.next();
//      System.out.println(entry.getKey().toString());
//      System.out.println(entry.getValue().toString());
//    }
    System.out.print(off_line_user);
    return off_line_user;
  }

  @MessageMapping("/hello")
  @SendTo("/topic/greetings")
  public ChatMessage greeting(ChatMessage message) throws Exception {
    String date = simpleDateFormat.format(new Date());
    String content = date + "【" + message.getName() + "】说：" + message.getContent();
    message.setDate(date);
    message.setContent(content);
    Thread.sleep(1000);
    return message;
  }

  @MessageMapping("/private")
  public void privatechat(ChatMessage message) throws Exception {

    String ctx = message.getContent();
    String userid = message.getName();
    String touser = message.getReceiver();

    String date = simpleDateFormat.format(new Date());
//    String content =date+"【"+userid+"】对你说：" + ctx;
    String content = ctx;

//    String contents =date+" 你对【"+ touser +"】说："+ ctx;
    String contents = ctx;

    //Send message to sender(Yourself)
    template.convertAndSendToUser(userid,"/topic/private",new ChatMessage(userid,contents,touser,date));
    //Save message history
    //Implementation:





    //Implementation completed
    Thread.sleep(1000);
    if("机器人".equals(touser)){
      touser = userid;
      String url = "http://www.tuling123.com/openapi/api";
      //请填写自己的key
      String post = "{\"key\": \"\",\"info\": \""+ctx+"\",\"userid\":\""+userid+"\"}";
      String body = Jsoup.connect(url).method(Connection.Method.POST)
        .requestBody(post)
        .header("Content-Type", "application/json; charset=utf-8")
        .ignoreContentType(true).execute().body();
      String text = JSONObject.parseObject(body).getString("text");
      content =date+"【机器人】对你说：" + text;
      template.convertAndSendToUser(touser,"/topic/private",new ChatMessage("机器人",content,"机器人",date));
      return;
    }

    //Send message to receiver
    template.convertAndSendToUser(touser,"/topic/private",new ChatMessage(userid,content,touser,date));
    com.example.demo.entity.ChatMessage msg_in_db = new com.example.demo.entity.ChatMessage();
    msg_in_db.setSender(userid);
    msg_in_db.setReceiver(touser);
    msg_in_db.setContent(content);
    msg_in_db.setSendTime(date);

    iChatMessageService.insert(msg_in_db);

  }

  //返回该用户所有好友的JSON Object
  @RequestMapping("/get_friends_set")
  public JSONObject getFriendsSet(String username){
//    Set<String> friendSet = new HashSet<>();
    Users usr = new Users();
    usr.setName(username);
    List<Friends> friendsList =  iFriendsService.selectByUser(usr);
    JSONObject friends = new JSONObject();

    for(Friends f : friendsList){
      System.out.println("username: " + username + "Friend user1: "+ f.getUser1() + " Friend user2:" + f.getUser2());
      if(f.getUser1().equals(username)){
        friends.put(f.getUser2() +"666",f.getUser2());
      }else {
        friends.put(f.getUser1() +"666",f.getUser1());
      }
    }
    return friends;
  }

  //A user disconnect then add it into offline user set
  @RequestMapping("/disconnect")
  public boolean userDisconnect(HttpServletRequest request) throws Exception{
    String userName = request.getParameter("username");
    all_users.add(userName);
    return true;
  }

  @RequestMapping("/load_hist")
  public boolean loadHist(HttpServletRequest request) throws Exception {
      System.out.println("Loading......................");
      String userName = request.getParameter("username");
      //Read message from data
      // ...
      Users usr = new Users();
      usr.setName(userName);
      List<com.example.demo.entity.ChatMessage> messages_from_db = iChatMessageService.selectByUser(usr);
      //将entity中chatmessage类型转换为model中的
      List<ChatMessage> hist_message = new ArrayList<>();
      if (messages_from_db.size() > 0) {
        for (com.example.demo.entity.ChatMessage m : messages_from_db) {
          ChatMessage msg = new ChatMessage();
          msg.setName(m.getSender());
          msg.setReceiver(m.getReceiver());
          msg.setContent(m.getContent());
          msg.setDate(m.getSendTime());
          hist_message.add(msg);
        }
      }
      //Get hist_messages
      if(hist_message.size() > 0) {
          loadUserHistory(userName, hist_message);
      }
      return true;
  }

  @RequestMapping("/rm_ol_usr")
  public boolean removeOnlineUser(HttpServletRequest request) throws Exception{
      String userName = request.getParameter("username");
      all_users.add(userName);
      return true;
  }
  //Load all a user history message from database and show on the page
  public void loadUserHistory(String userName, List<ChatMessage> messages) throws Exception{
      for(ChatMessage m: messages){
          String content = m.getContent();
          String userid = m.getName();
          String touser = m.getReceiver();
          String date = m.getDate();
//          String content =date+"【"+userid+"】对你说：" + ctx;
//          String contents =date+" 你对【"+ touser +"】说："+ ctx;
          //Send message to sender(Yourself)
          //sender = user
          template.convertAndSendToUser(userName, "/topic/private", new ChatMessage(userid,content,touser,date));
//          if (userName.equals(touser)){
//              System.out.println("Username: "+ userName+ " ==================== touser: " + touser);
//              //这行就运行不了了奇怪
//              template.convertAndSendToUser(userid,"/topic/private", new ChatMessage(userid,content,touser,date));
//          }
//          else{
//              System.out.println("Username: "+ userName+ " ==================== touser: " + touser);
//              template.convertAndSendToUser(userid,"/topic/private", new ChatMessage(,content,touser,date));
//          }
          Thread.sleep(200);
      }

  }
}
