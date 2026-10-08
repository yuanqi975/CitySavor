package com.hmdp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hmdp.kafka")
public class KafkaTopicProperties {

    private String voucherOrderTopic = "voucher-order-topic";
    private String voucherOrderDltTopic = "voucher-order-topic.DLT";
    private String voucherOrderDltGroup = "voucher-order-dlt-group";
    private long sendTimeoutSeconds = 12L;

    public String getVoucherOrderTopic() {
        return voucherOrderTopic;
    }

    public void setVoucherOrderTopic(String voucherOrderTopic) {
        this.voucherOrderTopic = voucherOrderTopic;
    }

    public String getVoucherOrderDltTopic() {
        return voucherOrderDltTopic;
    }

    public void setVoucherOrderDltTopic(String voucherOrderDltTopic) {
        this.voucherOrderDltTopic = voucherOrderDltTopic;
    }

    public String getVoucherOrderDltGroup() {
        return voucherOrderDltGroup;
    }

    public void setVoucherOrderDltGroup(String voucherOrderDltGroup) {
        this.voucherOrderDltGroup = voucherOrderDltGroup;
    }

    public long getSendTimeoutSeconds() {
        return sendTimeoutSeconds;
    }

    public void setSendTimeoutSeconds(long sendTimeoutSeconds) {
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }
}
