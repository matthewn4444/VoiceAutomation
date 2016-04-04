package com.matthewn4444.voiceautomation.lights;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import com.matthewn4444.voiceautomation.LazyPref;
import com.matthewn4444.voiceautomation.R;
import com.matthewn4444.voiceautomation.SpeechCategory;
import com.matthewn4444.voiceautomation.SpeechController.SpeechModel;

import java.security.InvalidParameterException;

public class LightsSpeechCategory extends SpeechCategory {
    private final ILightController mLightController;
    private final ConnectivityManager mConManager;

    // Commands
    private static final String COMMAND_BRIGHTER = "brighter";
    private static final String COMMAND_DIMMER = "dimmer";
    private static final String COMMAND_TURN_OFF = "turn off";
    private static final String COMMAND_TURN_ON = "turn on";
    private static final String COMMAND_PERCENT = "percent";

    public interface ILightController {
        public interface OnConnectionChangedListener {
            public void onConnectionChanged(int lightsConnected, boolean justConnected);
            public void onCommandFinished();
            public void onError(Exception e);
        }
        public boolean isAvailable();
        public boolean isOn();
        public void setBrightnessPercentage(int percentage);
        public void setBrightnessPercentage(int percentage, int duration);
        public int getBrightnessPercentage();
        public void turnOff();
        public void turnOn();
        public void connect();
        public void disconnect();
        public void setOnConnectionChangedListener(OnConnectionChangedListener listener);
    }

    public LightsSpeechCategory(Context ctx, ILightController controller) {
        super(ctx, new LightsPresenter(ctx), ctx.getString(R.string.settings_default_activation_command_lights),
                ctx.getString(R.string.settings_general_light_activation_command_key),
                ctx.getString(R.string.assets_lights_grammer_filename),
                SpeechModel.DEFAULT,
                ctx.getString(R.string.prompt_adjust_lights));
        mLightController = controller;
        mConManager = ((ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE));
    }

    public static boolean areLightsEnabled(Context ctx) {
        return LazyPref.getBool(ctx, R.string.settings_general_light_enable_lights_key, true);
    }

    public int getBrightness() {
        return mLightController.getBrightnessPercentage();
    }

    public boolean isOn() {
        return mLightController.isOn();
    }

    @Override
    public void pause() {
        super.pause();
        mLightController.disconnect();
    }

    @Override
    public void resume() {
        super.resume();
        mLightController.connect();
    }

    @Override
    public void onResult(String result) {
        if (result != null) {
            if (mLightController.isAvailable()) {
                if (result.equals(COMMAND_TURN_OFF)) {
                    mLightController.turnOff();
                } else if (result.equals(COMMAND_TURN_ON)) {
                    mLightController.turnOn();
                } else if (result.equals(COMMAND_BRIGHTER)) {
                    int brightness = mLightController.getBrightnessPercentage();
                    mLightController.setBrightnessPercentage(brightness + getDimBrightenStep());
                } else if (result.equals(COMMAND_DIMMER)) {
                    int brightness = mLightController.getBrightnessPercentage();
                    mLightController.setBrightnessPercentage(brightness - getDimBrightenStep());
                } else if (result.endsWith(COMMAND_PERCENT)) {
                    String text = result.substring(0, result.lastIndexOf(COMMAND_PERCENT));
                    int percent = convertEnglishNumberStringToInteger(text);
                    mLightController.setBrightnessPercentage(percent);
                } else {
                    Toast.makeText(getContext(), "Light Command '" + result + "' not supported",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                ((LightsPresenter)getPresenter()).animateChange(mLightController.getBrightnessPercentage(),
                        mLightController.isOn());

                // Record the last time the user edited the lights to avoid light automation changes
                LightsAutomator.disableAutomation(getContext());
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return mLightController.isAvailable() && isWifiConnected()
                && LightsAutomator.isAutomationAllowedBySSID(getContext());     // TODO Temp till lifx fixes lan
    }

    private int getDimBrightenStep() {
        return LazyPref.getIntDefaultRes(getContext(),
                R.string.settings_general_light_dim_brighten_step_key,
                R.integer.settings_default_dim_brighten_step);
    }

    private int convertEnglishNumberStringToInteger(String number) {
        String[] words = number.split(" ");
        int sum = 0;
        for (String word: words) {
            InvalidParameterException e = new InvalidParameterException(
                    "It is impossible to get here with " + word);
            int len = word.length();
            if (len >= 3) {
                char c = word.charAt(2);
                switch (len) {
                    case 3:
                        switch (c) {
                            case 'e':
                                sum += 1;
                                break;
                            case 'x':
                                sum += 6;
                                break;
                            case 'o':
                                sum += 2;
                                break;
                            case 'n':
                                sum += 10;
                                break;
                            default:
                                throw e;
                        }
                        break;
                    case 4:
                        switch (c) {
                            case 'u':
                                sum += 4;
                                break;
                            case 'v':
                                sum += 5;
                                break;
                            case 'n':
                                sum += 9;
                                break;
                            case 'r':       // Zero
                                break;
                            default:
                                throw e;
                        }
                        break;
                    case 5:
                        switch (c) {
                            case 'g':
                                sum += 8;
                                break;
                            case 'f':
                                sum += 50;
                                break;
                            case 'r':
                                sum += word.charAt(0) == 'f' ? 40 : 3;
                                break;
                            case 'v':
                                sum += 7;
                                break;
                            case 'x':
                                sum += 60;
                                break;
                            default:
                                throw e;
                        }
                        break;
                    case 6:
                        c = word.charAt(3);
                        switch (c) {
                            case 'h':
                                sum += 80;
                                break;
                            case 'v':
                                sum += 11;
                                break;
                            case 'e':
                                sum += 90;
                                break;
                            case 'l':
                                sum += 12;
                                break;
                            case 'r':
                                sum += 30;
                                break;
                            case 'n':
                                sum += 20;
                                break;
                            default:
                                throw e;
                        }
                        break;
                    case 7:
                        switch (c) {
                            case 'f':
                                sum += 15;
                                break;
                            case 'x':
                                sum += 16;
                                break;
                            case 'n':
                                sum += 100;
                                break;
                            case 'v':
                                sum += 70;
                                break;
                            default:
                                throw e;
                        }
                        break;
                    case 8:
                        switch (c) {
                            case 'u':
                                sum += 14;
                                break;
                            case 'n':
                                sum += 19;
                                break;
                            case 'i':
                                sum += 13;
                                break;
                            case 'g':
                                sum += 18;
                                break;
                            default:
                                throw e;
                        }
                        break;
                    case 9:
                        sum += 17;
                        break;
                    default:
                        throw e;
                }
            }
        }
        return Math.min(sum, 100);
    }

    private boolean isWifiConnected() {
        NetworkInfo info = mConManager.getActiveNetworkInfo();
        return info.getType() == ConnectivityManager.TYPE_WIFI && info.isConnected();
    }
}
