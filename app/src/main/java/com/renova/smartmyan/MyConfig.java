package com.renova.smartmyan;

public class MyConfig {
    private static boolean soundOn;
    private static boolean switchIme;

    public static boolean isSoundOn() {
        return soundOn;
    }

    public static void setSoundOn(boolean soundOn) {
        MyConfig.soundOn = soundOn;
    }

    public static boolean isSwitch() {
        return switchIme;
    }

    public static void setSwitchIme(boolean switchIme) {
        MyConfig.switchIme = switchIme;
    }
}
