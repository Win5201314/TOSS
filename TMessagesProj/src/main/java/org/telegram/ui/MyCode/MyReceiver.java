package org.telegram.ui.MyCode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.GroupCreateFinalActivity;
import org.telegram.ui.LoginActivity;
import org.telegram.ui.NewContactActivity;
import org.telegram.ui.ProfileActivity;

public class MyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        if (action.equals("register")) {
            //注册账号
            LoginActivity.loginToZX();
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
            TLObject object = MessagesController.getInstance().getUserOrChat(link.trim());
            TLRPC.Chat chat = null;
            if (object instanceof TLRPC.Chat) {
                chat = (TLRPC.Chat) object;
                if (chat.min) {
                    chat = null;
                }
            }
            Log.d("TAG", link + "<-------------");
            if (chat != null) {
                int chat_id = chat.id;
                Log.d("TAG", chat_id + "<-----进群----12----");
                //chat_id是关键 群链接和chat_id之间的关系  1184865261
                MessagesController.getInstance().addUserToChat(chat_id, UserConfig.getCurrentUser(), null, 0, null, null);
            }
        } else if (action.equals("leaveGroup")) {
            //有些群没有，有些群被封了
            //退群链接[去掉"@"字符之后的部分] openByUserName
            String link = intent.getStringExtra("link");
            TLObject object = MessagesController.getInstance().getUserOrChat(link.trim());
            TLRPC.Chat chat = null;
            if (object instanceof TLRPC.Chat) {
                chat = (TLRPC.Chat) object;
                if (chat.min) {
                    chat = null;
                }
            }
            Log.d("TAG", link + "<-------------");
            if (chat != null) {
                int chat_id = chat.id;
                Log.d("TAG", chat_id + "<----退群-----12----");
                //chat_id是关键 群链接和chat_id之间的关系  1184865261
                MessagesController.getInstance().deleteUserFromChat(chat_id, MessagesController.getInstance().getUser(UserConfig.getClientUserId()), null);
            }
        } else if (action.equals("addFriendsGroup")) {
            //群里逐个发消息，加好友

        } else if (action.equals("groupNumber")) {
            //群链接[去掉"@"字符之后的部分] 自己建的群和别人的群有区别 openByUserName
            String link = intent.getStringExtra("link");
            TLObject object = MessagesController.getInstance().getUserOrChat(link.trim());
            TLRPC.Chat chat = null;
            if (object instanceof TLRPC.Chat) {
                chat = (TLRPC.Chat) object;
                if (chat.min) {
                    chat = null;
                }
            }
            Log.d("TAG", link + "<-------------");
            if (chat != null) {
                int chat_id = chat.id;
                if (chat_id != 0) {
                    Log.d("TAG", chat_id + "<----统计指定群人数---------");
                    ProfileActivity.getChannelParticipants(chat_id);
                    //Log.d("TAG", ProfileActivity.info.participants_count + "群人数" + "========================");
                    /*TLRPC.Chat chat1 = MessagesController.getInstance().getChat(chat_id);
                    int count = chat1.participants_count;
                    String newString = LocaleController.formatPluralString("Members", count);
                    Log.d("TAG", count + "群人数" + "========================" + newString);*/
                }
            }
        } else if (action.equals("createGroup")) {
            //建群
            String groupName = intent.getStringExtra("groupName");
            if (!TextUtils.isEmpty(groupName)) {
                GroupCreateActivity.addPeople(groupName.trim());
            }
        } else if (action.equals("contactNumber")) {
            //统计通讯录好友人数
            GroupCreateActivity.countContactNumber();
        } else if (action.equals("addContactJoinGroup")) {
            //拉通讯录好友进指定群
            //指定的群名字
            String groupName = intent.getStringExtra("groupName");
            //拉进去的人数
            String number = intent.getStringExtra("number");
        }
    }

}
