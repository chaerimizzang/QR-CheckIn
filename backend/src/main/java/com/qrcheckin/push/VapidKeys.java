package com.qrcheckin.push;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 웹 푸시 서버 키(VAPID). 환경 변수(VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY)가 있으면 그것을,
 * 없으면 처음 한 번 만들어 DB(app_setting)에 저장해 두고 계속 쓴다. 키가 바뀌면 기존 구독은 다시 해야 한다.
 */
@Component
public class VapidKeys {

    static final String PUBLIC = "vapid.public";
    static final String PRIVATE = "vapid.private";

    private final AppSettingRepository settings;
    private final String envPublic;
    private final String envPrivate;
    private volatile Keys keys;

    public VapidKeys(AppSettingRepository settings,
                     @Value("${app.push.vapid-public-key:}") String envPublic,
                     @Value("${app.push.vapid-private-key:}") String envPrivate) {
        this.settings = settings;
        this.envPublic = envPublic == null ? "" : envPublic.trim();
        this.envPrivate = envPrivate == null ? "" : envPrivate.trim();
    }

    public record Keys(byte[] publicKey, ECPrivateKey privateKey) {
        public String publicKeyBase64() {
            return WebPushCrypto.b64(publicKey);
        }
    }

    public Keys get() {
        Keys k = keys;
        if (k == null) {
            synchronized (this) {
                if (keys == null) {
                    keys = load();
                }
                k = keys;
            }
        }
        return k;
    }

    private Keys load() {
        if (!envPublic.isEmpty() && !envPrivate.isEmpty()) {
            return new Keys(WebPushCrypto.unb64(envPublic), WebPushCrypto.decodePrivate(WebPushCrypto.unb64(envPrivate)));
        }
        String pub = settings.findById(PUBLIC).map(AppSetting::getValue).orElse(null);
        String priv = settings.findById(PRIVATE).map(AppSetting::getValue).orElse(null);
        if (pub == null || priv == null) {
            KeyPair pair = WebPushCrypto.newKeyPair();
            pub = WebPushCrypto.b64(WebPushCrypto.encodePublic((ECPublicKey) pair.getPublic()));
            priv = WebPushCrypto.b64(WebPushCrypto.encodePrivate((ECPrivateKey) pair.getPrivate()));
            settings.save(new AppSetting(PUBLIC, pub));
            settings.save(new AppSetting(PRIVATE, priv));
        }
        return new Keys(WebPushCrypto.unb64(pub), WebPushCrypto.decodePrivate(WebPushCrypto.unb64(priv)));
    }
}
