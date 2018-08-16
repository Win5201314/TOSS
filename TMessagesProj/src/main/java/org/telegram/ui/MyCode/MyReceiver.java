package org.telegram.ui.MyCode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.LoginActivity;
import org.telegram.ui.NewContactActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;

public class MyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        if (action.equals("register")) {
            //注册账号
            LoginActivity.loginToZX();
            //注册填写名字界面
            //LoginActivity.setFirstNameAndLastName("+86", "", "", "", "");
        } else if (action.equals("sendPhoneNumber")) {
            //登录 发送手机号到Telegram,接收短信验证码
            //发送手机号
            String code = intent.getStringExtra("code");
            String phoneNumber = intent.getStringExtra("phoneNumber");
            LoginActivity.sendPhoneNumber(code, phoneNumber, null);
        } else if (action.equals("sendSMS")) {
           //把接收到的短信验证码和对应的手机号发到Telegram
            String code = intent.getStringExtra("code");
            LoginActivity.sendSMS(code);
        } else if (action.equals("addContact")) {
            //添加通讯录加好友
            String firstName = intent.getStringExtra("firstName");
            String lastName = intent.getStringExtra("lastName");
            String code = intent.getStringExtra("code");
            String phoneNumber = intent.getStringExtra("phoneNumber");
            NewContactActivity.addContacts(firstName, lastName, code, phoneNumber);
        } else if (action.equals("joinGroup")) {
            //有些群没有则不会进，有些群被封了，也不会进去
            //进群链接[去掉"@"字符之后的部分]  openByUserName
            String link = intent.getStringExtra("link");
            if (TextUtils.isEmpty(link)) return;
            int chat_id = makeChatId(link);
            Log.d("TAG", chat_id + "<-----进群----12----");
            //chat_id是关键 群链接和chat_id之间的关系  1184865261
            MessagesController.getInstance().addUserToChat(chat_id, UserConfig.getCurrentUser(), null, 0, null, null);
        } else if (action.equals("leaveGroup")) {
            //有些群没有，有些群被封了
            //退群链接[去掉"@"字符之后的部分] openByUserName
            String link = intent.getStringExtra("link");
            int chat_id = makeChatId(link);
            Log.d("TAG", chat_id + "<----退群-----12----");
            //chat_id是关键 群链接和chat_id之间的关系  1184865261
            MessagesController.getInstance().deleteUserFromChat(chat_id, MessagesController.getInstance().getUser(UserConfig.getClientUserId()), null);
        } else if (action.equals("addFriendsGroup")) {
            //群里逐个发消息，加好友

        } else if (action.equals("createGroup")) {
            //建群
            String groupName = intent.getStringExtra("groupName");
            if (!TextUtils.isEmpty(groupName)) GroupCreateActivity.addPeople(groupName.trim());
        } else if (action.equals("contactNumber")) {
            //统计通讯录好友人数
            GroupCreateActivity.countContactNumber();
        } else if (action.equals("addContactJoinGroup")) {
            //拉通讯录好友进指定群
            //指定的群链接@不要
            String link = intent.getStringExtra("link");
            //拉进去的人数
            String number = intent.getStringExtra("number");
            if (TextUtils.isEmpty(link) || TextUtils.isEmpty(number)) return;
            ArrayList<TLRPC.User> contacts = new ArrayList<>();
            ArrayList<TLRPC.TL_contact> arrayList  = ContactsController.getInstance().contacts;
            for (int a = 0; a < arrayList.size(); a++) {
                TLRPC.User user = MessagesController.getInstance().getUser(arrayList.get(a).user_id);
                if (user == null || user.self || user.deleted) {
                    continue;
                }
                contacts.add(user);
            }
            int chat_id = makeChatId(link);
            //将通讯录所有人拉进去，人数可以设置，也可以随机某一个人
            int num = Integer.parseInt(number);
            for (TLRPC.User user : contacts) {
                Log.d("TAG", "==2" + user.username);
                MessagesController.getInstance().addUserToChatBycontact(chat_id, user, 0);
            }
        } else if (action.equals("sendText")) {
            //对指定群发送纯文本消息
            String text = intent.getStringExtra("text");
            //群链接[去掉"@"字符之后的部分] 自己建的群和别人的群有区别 openByUserName
            String link = intent.getStringExtra("link");
            if (TextUtils.isEmpty(link) || TextUtils.isEmpty(text)) return;
            int chat_id = makeChatId(link);
            long dialog_id = chat_id > 0 ? -chat_id : AndroidUtilities.makeBroadcastId(chat_id);
            ChatActivityEnterView.sendMSG(text, dialog_id);
        } else if (action.equals("groupCount")) {
            //统计指定群的当前群人数
            /*String link = "BHgroup2";
            int chat_id = makeChatId(link);
            long dialog_id = chat_id > 0 ? -chat_id : AndroidUtilities.makeBroadcastId(chat_id);
            ChatActivity.sendMsgToContact(dialog_id, "lo", "+8613502820304");*/

            //群链接[去掉"@"字符之后的部分] 自己建的群和别人的群有区别 openByUserName
            String link = intent.getStringExtra("link");
            if (TextUtils.isEmpty(link)) return;
            int chat_id = makeChatId(link);
            //这里最后一个参数控制请求次数，true代表随意次请求，否则，只能请求一次
            MessagesController.getInstance().loadFullChat(chat_id, 0, true);
        } else if (action.equals("sendPicture")) {
            //对指定群发送图片消息
            //群链接[去掉"@"字符之后的部分] 自己建的群和别人的群有区别 openByUserName
            String link = intent.getStringExtra("link");
            //图片路径
            String path  = intent.getStringExtra("path");
            if (TextUtils.isEmpty(link) || TextUtils.isEmpty(path)) return;
            int chat_id = makeChatId(link);
            long dialog_id = chat_id > 0 ? -chat_id : AndroidUtilities.makeBroadcastId(chat_id);
            ChatActivity.sendPicture(dialog_id, path);
        } else if (action.equals("loginout")) {
            //账号退出
            MessagesController.getInstance().performLogout(true);
        }
    }

    /**
     *
     * @param link 群链接【前面不带@字符的】
     */
    private int makeChatId(String link) {
        TLObject object = MessagesController.getInstance().getUserOrChat(link.trim());
        TLRPC.Chat chat = null;
        if (object instanceof TLRPC.Chat) {
            chat = (TLRPC.Chat) object;
            if (chat.min) chat = null;
        }
        if (chat != null) {
            int chat_id = chat.id;
            if (chat_id != 0) return chat_id;
        }
        return -1;
    }

    private TLRPC.User makeUser(String username) {//openByUserName
        TLObject object = MessagesController.getInstance().getUserOrChat(username);
        TLRPC.User user = null;
        if (object instanceof TLRPC.User) {
            user = (TLRPC.User) object;
            if (user.min) {
                user = null;
            }
        }
        /*
        TLRPC.User user = MessagesController.getInstance().getUser(user_id);
        if (user == null) {
            return;
        }*/
        return user;
    }


    /**
     * 分享自己的通讯录到指定群
     * @param link 群链接【前面不带@字符的】
     */
    private void shareContactToGroup(String link) {
        int chat_id = makeChatId(link);
        long dialog_id = chat_id > 0 ? -chat_id : AndroidUtilities.makeBroadcastId(chat_id);
        SendMessagesHelper.getInstance().sendMessage(UserConfig.getCurrentUser(), dialog_id, null, null, null);
    }

    /**
     * 删除指定群聊天列表
     * @param link 群链接【前面不带@字符的】
     */
    private void deleteGroupMsg(String link) {
        int chat_id = makeChatId(link);
        long dialog_id = chat_id > 0 ? -chat_id : AndroidUtilities.makeBroadcastId(chat_id);
        MessagesController.getInstance().deleteDialog(dialog_id, 0);
    }

}
