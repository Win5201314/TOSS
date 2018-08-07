package org.telegram.ui.MyCode;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HttpCilentUtil {

    /**
     * 众享登录
     *
     * @throws IOException
     */
    public static Response loginZhongXiang(String url) throws IOException {
        OkHttpClient mOkHttpClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        Call call = mOkHttpClient.newCall(request);
        return call.execute();
    }

    /**
     * 获取众享的手机号
     *
     * @throws IOException
     */
    public static Response getPhoneNumberFromZhongXiang(String url) throws IOException {
        OkHttpClient mOkHttpClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        Call call = mOkHttpClient.newCall(request);
        return call.execute();
    }

    /**
     * 获取众享的验证码
     *
     * @throws IOException
     */
    public static Response getCodeFromZhongXiang(String url) throws IOException {
        OkHttpClient mOkHttpClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        Call call = mOkHttpClient.newCall(request);
        return call.execute();
    }

}
