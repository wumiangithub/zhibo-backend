package com.zhibo.liveprovider;

public class ProviderNotAvailableException extends RuntimeException {

    public ProviderNotAvailableException() {
        super("该直播服务商尚未接入");
    }

    public ProviderNotAvailableException(String message) {
        super(message);
    }
}
