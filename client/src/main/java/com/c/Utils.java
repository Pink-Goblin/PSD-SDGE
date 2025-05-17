package com.c;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Utils {
    private static Scanner scanner = new Scanner(System.in);

    public static String ReadAny() {
        return scanner.nextLine();
    }

    public static int ReadInt() {
        int input = -1;
        try {
            input = scanner.nextInt();
            scanner.nextLine();
        } catch (Exception e) {
            scanner = new Scanner(System.in);
            return input;
        }

        return input;
    }

    public static String ReadToken() {
        String input = scanner.next();
        scanner.nextLine();
        return input;
    }

    public static Map<String, String> getGroups() {
        Map<String, String> groups = new HashMap<String, String>();
        groups.put("Debug", "localhost:3000");
        return groups;
    }
}
