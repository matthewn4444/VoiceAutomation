package com.matthewn4444.voiceautomation;

public class Command {
    public final static String DefaultThreshold = "1e-1";

    private String mCommand;
    private String mThreshold;

    public Command(String command) {
        this(command, DefaultThreshold);
    }
    public Command(String command, String threshold) {
        setCommand(command);
        setThreshold(threshold);
    }

    public void setCommand(String command) {
        mCommand = command;
    }

    public void setThreshold(String threshold) {
        mThreshold = threshold != null ? threshold : DefaultThreshold;
    }

    public String getCommand() {
        return mCommand;
    }

    public String getThreshold() {
        return mThreshold;
    }

    public String getGrammerLine() {
        return mCommand + " /" + mThreshold + "/";
    }
}
