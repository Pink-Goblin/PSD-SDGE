package com.c;

import java.util.UUID;

public class Client {
    private static Client self = new Client("anonymous_" + UUID.randomUUID());

    private String Username;

    private Client(String username) {
        this.Username = username;
    }

    public static String getUsername() {
        return self.Username;
    }

    public static void setUsername(String username) {
        self.Username = username;
    }
}
