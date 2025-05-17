package com.c;

public class AsyncReadAny implements Runnable {

    private String input;

    public AsyncReadAny() {
        input = "";
    }

    @Override
    public void run() {
        try {
            input = Utils.ReadAny();
        } catch (Exception e) {
            input = "";
        }
    }

    public boolean hasInput() {
        return input != "";
    }

    public String getInput() {
        return input;
    }
}
