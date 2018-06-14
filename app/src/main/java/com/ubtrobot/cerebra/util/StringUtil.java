package com.ubtrobot.cerebra.util;

/**
 * A util to deal with string.
 */

public class StringUtil {
    public static int strToInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
//            e.printStackTrace();
            return defaultValue;
        }
    }

    public static boolean strToBool(String str, boolean defaultValue) {
        try {
            return Boolean.parseBoolean(str);
        } catch (Exception e) {
//            e.printStackTrace();
            return defaultValue;
        }
    }
}
