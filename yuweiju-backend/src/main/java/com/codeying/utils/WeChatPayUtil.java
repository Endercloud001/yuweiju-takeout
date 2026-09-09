package com.codeying.utils;

import com.codeying.properties.SkyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.auth.AutoUpdateCertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 微信支付工具类（JSAPI）。
 *
 * @author Endercloud
 */
@Component
public class WeChatPayUtil {

    private final SkyProperties skyProperties;
    private final ObjectMapper objectMapper;
    private volatile CloseableHttpClient httpClient;
    private volatile PrivateKey merchantPrivateKey;

    public WeChatPayUtil(SkyProperties skyProperties, ObjectMapper objectMapper) {
        this.skyProperties = skyProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 发起微信支付下单并返回小程序支付参数。
     *
     * @param description      商品描述
     * @param outTradeNo       商户订单号
     * @param totalAmountYuan  金额（元）
     * @param openid           用户 openid
     * @return 支付参数
     */
    public PayResult pay(String description, String outTradeNo, BigDecimal totalAmountYuan, String openid) {
        ensureInitialized();
        SkyProperties.WechatProperties wechat = skyProperties.getWechat();
        int totalFen = totalAmountYuan.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValue();

        ObjectNode requestBody = objectMapper.createObjectNode()
                .put("appid", wechat.getAppid())
                .put("mchid", wechat.getMchid())
                .put("description", description)
                .put("out_trade_no", outTradeNo)
                .put("notify_url", wechat.getNotifyUrl());
        requestBody.set("amount", objectMapper.createObjectNode()
                .put("total", totalFen)
                .put("currency", "CNY"));
        requestBody.set("payer", objectMapper.createObjectNode()
                .put("openid", openid));

        String prepayId = requestPrepayId(requestBody.toString());

        String timeStamp = String.valueOf(Instant.now().getEpochSecond());
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String packageStr = "prepay_id=" + prepayId;
        String signType = "RSA";
        String paySign = sign(wechat.getAppid(), timeStamp, nonceStr, packageStr);

        PayResult result = new PayResult();
        result.setTimeStamp(timeStamp);
        result.setNonceStr(nonceStr);
        result.setPackageStr(packageStr);
        result.setSignType(signType);
        result.setPaySign(paySign);
        return result;
    }

    private void ensureInitialized() {
        if (httpClient != null && merchantPrivateKey != null) return;
        synchronized (this) {
            if (httpClient != null && merchantPrivateKey != null) return;

            SkyProperties.WechatProperties wechat = skyProperties.getWechat();
            try (FileInputStream in = new FileInputStream(wechat.getPrivateKeyFilePath())) {
                this.merchantPrivateKey = PemUtil.loadPrivateKey(in);
            } catch (Exception e) {
                throw new IllegalStateException("微信支付私钥加载失败", e);
            }

            AutoUpdateCertificatesVerifier verifier = new AutoUpdateCertificatesVerifier(
                    new WechatPay2Credentials(
                            wechat.getMchid(),
                            new PrivateKeySigner(wechat.getMchSerialNo(), this.merchantPrivateKey)
                    ),
                    wechat.getApiV3Key().getBytes(StandardCharsets.UTF_8)
            );

            this.httpClient = WechatPayHttpClientBuilder.create()
                    .withMerchant(wechat.getMchid(), wechat.getMchSerialNo(), this.merchantPrivateKey)
                    .withValidator(new WechatPay2Validator(verifier))
                    .build();
        }
    }

    private String requestPrepayId(String body) {
        HttpPost httpPost = new HttpPost("https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi");
        httpPost.addHeader("Accept", "application/json");
        httpPost.addHeader("Content-type", "application/json; charset=utf-8");
        httpPost.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String respBody = EntityUtils.toString(response.getEntity());
            if (statusCode != 200 && statusCode != 201) {
                throw new IllegalStateException("微信下单失败: " + respBody);
            }
            JsonNode json = objectMapper.readTree(respBody);
            JsonNode prepayId = json.get("prepay_id");
            if (prepayId == null || prepayId.isNull() || prepayId.asText().isEmpty()) {
                throw new IllegalStateException("微信下单失败: prepay_id 为空");
            }
            return prepayId.asText();
        } catch (Exception e) {
            throw new IllegalStateException("微信下单失败", e);
        }
    }

    private String sign(String appid, String timeStamp, String nonceStr, String packageStr) {
        try {
            String message = appid + "\n" + timeStamp + "\n" + nonceStr + "\n" + packageStr + "\n";
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(merchantPrivateKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("微信支付签名失败", e);
        }
    }

    /**
     * 微信支付下单返回参数（用于前端发起支付）。
     *
     * @author Endercloud
     */
    public static class PayResult {
        private String timeStamp;
        private String nonceStr;
        private String packageStr;
        private String signType;
        private String paySign;

        /**
         * 获取时间戳（秒）。
         *
         * @return 时间戳
         */
        public String getTimeStamp() {
            return timeStamp;
        }

        /**
         * 设置时间戳（秒）。
         *
         * @param timeStamp 时间戳
         */
        public void setTimeStamp(String timeStamp) {
            this.timeStamp = timeStamp;
        }

        /**
         * 获取随机串。
         *
         * @return 随机串
         */
        public String getNonceStr() {
            return nonceStr;
        }

        /**
         * 设置随机串。
         *
         * @param nonceStr 随机串
         */
        public void setNonceStr(String nonceStr) {
            this.nonceStr = nonceStr;
        }

        /**
         * 获取 package 字段（通常包含 prepay_id）。
         *
         * @return package 字段
         */
        public String getPackageStr() {
            return packageStr;
        }

        /**
         * 设置 package 字段（通常包含 prepay_id）。
         *
         * @param packageStr package 字段
         */
        public void setPackageStr(String packageStr) {
            this.packageStr = packageStr;
        }

        /**
         * 获取签名类型。
         *
         * @return 签名类型
         */
        public String getSignType() {
            return signType;
        }

        /**
         * 设置签名类型。
         *
         * @param signType 签名类型
         */
        public void setSignType(String signType) {
            this.signType = signType;
        }

        /**
         * 获取支付签名。
         *
         * @return 支付签名
         */
        public String getPaySign() {
            return paySign;
        }

        /**
         * 设置支付签名。
         *
         * @param paySign 支付签名
         */
        public void setPaySign(String paySign) {
            this.paySign = paySign;
        }
    }
}
