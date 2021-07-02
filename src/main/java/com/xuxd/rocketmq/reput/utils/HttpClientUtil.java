package com.xuxd.rocketmq.reput.utils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * rocketmq-reput.
 *
 * @author xuxd
 * @date 2021-07-02 14:46:44
 **/
public final class HttpClientUtil {

    private static final OkHttpClient HTTP_CLIENT;

    private static final String UPLOAD_FILE_KEY = "file";

    static {
        HTTP_CLIENT = new OkHttpClient()
            .newBuilder()
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .build();
    }

    private HttpClientUtil() {
    }

    public static String upload(String filename, String filepath, String url,
        Map<String, String> headers) throws IOException {
        return upload(new File(filepath), url, headers);
    }

    public static String upload(File file, String url, Map<String, String> headers) throws IOException {
        RequestBody body = new MultipartBody
            .Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(UPLOAD_FILE_KEY, file.getName(), RequestBody.create(MultipartBody.FORM, file))
            .build();
        Request.Builder builder = new Request.Builder().url(url).post(body);
        headers.forEach((k, v) -> {
            builder.addHeader(k, v);
        });
        Request request = builder.build();
        Call call = HTTP_CLIENT.newCall(request);
        Response response = call.execute();
        return response.body().string();
    }

    public static String get(String url, Map<String, String> params) throws IOException {
        Request.Builder builder = new Request.Builder();
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        params.forEach((k, v) -> urlBuilder.addQueryParameter(k, v));
        builder.url(urlBuilder.build());
        Request request = builder.build();
        Call call = HTTP_CLIENT.newCall(request);
        Response response = call.execute();
        return response.body().string();
    }
}
