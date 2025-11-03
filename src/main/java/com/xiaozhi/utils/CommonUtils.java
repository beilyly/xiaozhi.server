package com.xiaozhi.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Random;

public class CommonUtils {

    public static final String BASE_PATH = System.getProperty("os.name").equals("Mac OS X")
            ? "avatar/"
            : System.getProperty("os.name").contains("Windows") ? "avatar/" : "avatar/";

    public static Integer CaptchaCode() {
        StringBuilder str = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            str.append(random.nextInt(10));
        }
        return Integer.valueOf(str.toString());
    }

    public static String left(String str, int index) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        String name = StringUtils.left(str, index);
        return StringUtils.rightPad(name, StringUtils.length(str), "*");
    }

    public static String right(String str, int end) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return StringUtils.leftPad(StringUtils.right(str, end), StringUtils.length(str), "*");
    }

    public static String around(String str, int index, int end) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return StringUtils.left(str, index).concat(StringUtils
                .removeStart(StringUtils.leftPad(StringUtils.right(str, end), StringUtils.length(str), "*"), "***"));
    }
}
