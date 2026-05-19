package com.callcenter.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class HashUtil {

    public static int hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));

            int h = ((digest[0] & 0xFF) << 24) |
                    ((digest[1] & 0xFF) << 16) |
                    ((digest[2] & 0xFF) << 8)  |
                    (digest[3] & 0xFF);

            return h & 0x7fffffff;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
