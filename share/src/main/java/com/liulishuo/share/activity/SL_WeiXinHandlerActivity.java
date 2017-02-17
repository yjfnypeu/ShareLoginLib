package com.liulishuo.share.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.liulishuo.share.LoginManager;
import com.liulishuo.share.ShareBlock;
import com.liulishuo.share.ShareManager;
import com.liulishuo.share.content.ShareContent;
import com.liulishuo.share.type.ContentType;
import com.liulishuo.share.type.ShareType;
import com.sina.weibo.sdk.exception.WeiboException;
import com.sina.weibo.sdk.net.AsyncWeiboRunner;
import com.sina.weibo.sdk.net.RequestListener;
import com.sina.weibo.sdk.net.WeiboParameters;
import com.tencent.mm.sdk.modelbase.BaseReq;
import com.tencent.mm.sdk.modelbase.BaseResp;
import com.tencent.mm.sdk.modelmsg.SendAuth;
import com.tencent.mm.sdk.modelmsg.SendMessageToWX;
import com.tencent.mm.sdk.modelmsg.WXImageObject;
import com.tencent.mm.sdk.modelmsg.WXMediaMessage;
import com.tencent.mm.sdk.modelmsg.WXMusicObject;
import com.tencent.mm.sdk.modelmsg.WXTextObject;
import com.tencent.mm.sdk.modelmsg.WXWebpageObject;
import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.IWXAPIEventHandler;
import com.tencent.mm.sdk.openapi.WXAPIFactory;

import org.json.JSONException;
import org.json.JSONObject;

import static com.liulishuo.share.ShareBlock.Config.weiXinAppId;

/**
 * Created by echo on 5/19/15.
 * 用来处理微信登录、微信分享的activity。这里真不知道微信非要个activity干嘛，愚蠢的设计!
 * 参考文档:https://open.weixin.qq.com/cgi-bin/showdocument?action=dir_list&t=resource/res_list&verify=1&id=open1419317853&lang=zh_CN
 */
public class SL_WeiXinHandlerActivity extends Activity implements IWXAPIEventHandler {

    /**
     * BaseResp的getType函数获得的返回值，1:第三方授权， 2:分享
     */
    private static final int TYPE_LOGIN = 1;

    private IWXAPI api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = WXAPIFactory.createWXAPI(this, ShareBlock.Config.weiXinAppId, true);
        api.handleIntent(getIntent(), this);
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (api != null) {
            api.handleIntent(getIntent(), this);
        }
        finish();
    }

    @Override
    public void onReq(BaseReq baseReq) {
        finish();
    }

    @Override
    public void onResp(BaseResp resp) {
        if (resp != null) {
            if (resp instanceof SendAuth.Resp && resp.getType() == TYPE_LOGIN) {
                parseLoginResp(this, (SendAuth.Resp) resp, LoginManager.listener);
            } else {
                parseShareResp(resp, ShareManager.listener);
            }
        }
        finish();
    }

    ///////////////////////////////////////////////////////////////////////////
    // login
    ///////////////////////////////////////////////////////////////////////////

    public static void login(@NonNull Context context) {
        String appId = ShareBlock.Config.weiXinAppId;
        if (TextUtils.isEmpty(appId)) {
            throw new NullPointerException("请通过shareBlock初始化WeiXinAppId");
        }

        IWXAPI api = WXAPIFactory.createWXAPI(context.getApplicationContext(), appId, true);
        api.registerApp(appId);

        SendAuth.Req req = new SendAuth.Req();
        req.scope = "snsapi_userinfo";
        api.sendReq(req); // 这里的请求的回调会在activity中收到，然后通过parseLoginResp方法解析
    }

    /**
     * 解析用户登录的结果
     */
    protected static void parseLoginResp(final Activity activity, SendAuth.Resp resp,
            @Nullable LoginManager.LoginListener listener) {
        // 有可能是listener传入的是null，也可能是调用静态方法前没初始化当前的类
/*
        if (mRespListener != null) {
            mRespListener.onLoginResp(resp);
        }
*/
        if (listener != null) {
            switch (resp.errCode) {
                case BaseResp.ErrCode.ERR_OK: // 登录成功
                    handlerLoginResp(activity, resp, listener); // 登录成功后开始通过code换取token
                    break;
                case BaseResp.ErrCode.ERR_USER_CANCEL:
                    listener.onCancel();
                    break;
                case BaseResp.ErrCode.ERR_AUTH_DENIED:
                    listener.onError("用户拒绝授权");
                    break;
                default:
                    listener.onError("未知错误");
            }
        }
    }

    private static void handlerLoginResp(Context context, SendAuth.Resp resp,
            final @Nullable LoginManager.LoginListener listener) {

        AsyncWeiboRunner runner = new AsyncWeiboRunner(context);
        WeiboParameters params = new WeiboParameters(null);
        params.put("appid", weiXinAppId);
        params.put("secret", ShareBlock.Config.weiXinSecret);
        params.put("code", resp.code);
        params.put("grant_type", "authorization_code");

        runner.requestAsync("https://api.weixin.qq.com/sns/oauth2/access_token", params, "GET", new RequestListener() {
            @Override
            public void onComplete(String s) {
                try {
                    JSONObject jsonObject = new JSONObject(s);
                    String token = jsonObject.getString("access_token");
                    String openid = jsonObject.getString("openid");
                    long expires_in = jsonObject.getLong("expires_in");

                    if (listener != null) {
                        listener.onSuccess(token, openid, expires_in, jsonObject.toString());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onWeiboException(WeiboException e) {
                if (listener != null) {
                    listener.onError(e.getMessage());
                }
            }
        });
    }
    
    /*public static void setRespListener(LoginRespListener respListener) {
        mRespListener = respListener;
    }*/

   /*public interface LoginRespListener {

        void onLoginResp(SendAuth.Resp resp);
    }*/

    ///////////////////////////////////////////////////////////////////////////
    // share
    ///////////////////////////////////////////////////////////////////////////

    public void sendShareMsg(@NonNull Context context, @NonNull ShareContent shareContent,
            @ShareType String shareType) {
        String weChatAppId = ShareBlock.Config.weiXinAppId;
        if (TextUtils.isEmpty(weChatAppId)) {
            throw new NullPointerException("请通过shareBlock初始化WeChatAppId");
        }

        IWXAPI IWXAPI = WXAPIFactory.createWXAPI(context, weChatAppId, true);
        IWXAPI.registerApp(weChatAppId);

        SendMessageToWX.Req req = getReq(shareContent, shareType);
        IWXAPI.sendReq(req);
    }

    /**
     * 解析分享到微信的结果
     */
    protected static void parseShareResp(BaseResp resp, ShareManager.ShareStateListener listener) {
        if (listener != null) {
            switch (resp.errCode) {
                case BaseResp.ErrCode.ERR_OK:
                    listener.onSuccess();
                    break;
                case BaseResp.ErrCode.ERR_USER_CANCEL:
                    listener.onCancel();
                    break;
                case BaseResp.ErrCode.ERR_AUTH_DENIED:
                    listener.onError("用户拒绝授权");
                    break;
                case BaseResp.ErrCode.ERR_SENT_FAILED:
                    listener.onError("发送失败");
                    break;
                case BaseResp.ErrCode.ERR_COMM:
                    listener.onError("一般错误");
                    break;
                default:
                    listener.onError("未知错误");
            }
        }
    }

    @NonNull
    private SendMessageToWX.Req getReq(@NonNull ShareContent shareContent, @ShareType String shareType) {
        // 建立信息体
        WXMediaMessage msg = new WXMediaMessage(getShareObject(shareContent));
        msg.title = shareContent.getTitle();
        msg.description = shareContent.getSummary();
        msg.thumbData = shareContent.getImageBmpBytes();

        // 发送信息
        SendMessageToWX.Req req = new SendMessageToWX.Req();
        req.transaction = String.valueOf(System.currentTimeMillis());
        req.message = msg;
        if (shareType.equals(ShareType.WEIXIN_FRIEND)) {
            req.scene = SendMessageToWX.Req.WXSceneSession;
        } else {
            req.scene = SendMessageToWX.Req.WXSceneTimeline;
        }
        return req;
    }

    private WXMediaMessage.IMediaObject getShareObject(@NonNull ShareContent shareContent) {
        WXMediaMessage.IMediaObject mediaObject;
        switch (shareContent.getType()) {
            case ContentType.TEXT:
                // 纯文字
                mediaObject = getTextObj(shareContent);
                break;
            case ContentType.PIC:
                // 纯图片
                mediaObject = getImageObj(shareContent);
                break;
            case ContentType.WEBPAGE:
                // 网页
                mediaObject = getWebPageObj(shareContent);
                break;
            case ContentType.MUSIC:
                // 音乐
                mediaObject = getMusicObj(shareContent);
                break;
            default:
                throw new UnsupportedOperationException("不支持的分享内容");
        }
        if (!mediaObject.checkArgs()) {
            throw new IllegalArgumentException("分享信息的参数类型不正确");
        }
        return mediaObject;
    }

    private WXMediaMessage.IMediaObject getTextObj(ShareContent shareContent) {
        WXTextObject text = new WXTextObject();
        text.text = shareContent.getSummary();
        return text;
    }

    private WXMediaMessage.IMediaObject getImageObj(ShareContent shareContent) {
        WXImageObject image = new WXImageObject();
        image.imageData = shareContent.getImageBmpBytes();
        return image;
    }

    private WXMediaMessage.IMediaObject getWebPageObj(ShareContent shareContent) {
        WXWebpageObject webPage = new WXWebpageObject();
        webPage.webpageUrl = shareContent.getURL();
        return webPage;
    }

    private WXMediaMessage.IMediaObject getMusicObj(ShareContent shareContent) {
        WXMusicObject music = new WXMusicObject();
        //Str1+"#wechat_music_url="+str2 ;str1是网页地址，str2是音乐地址。
        music.musicUrl = shareContent.getURL() + "#wechat_music_url=" + shareContent.getMusicUrl();
        return music;
    }
}
