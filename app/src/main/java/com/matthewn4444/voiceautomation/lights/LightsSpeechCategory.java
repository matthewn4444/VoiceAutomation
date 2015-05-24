package com.matthewn4444.voiceautomation.lights;

import android.content.Context;
import android.graphics.Color;
import android.widget.Toast;

import com.matthewn4444.voiceautomation.R;
import com.matthewn4444.voiceautomation.SpeechCategory;
import com.matthewn4444.voiceautomation.SpeechController.SpeechModel;

import java.security.InvalidParameterException;

public class LightsSpeechCategory extends SpeechCategory {
    private static final int ON_COLOR = Color.YELLOW;
    private static final int OFF_COLOR = Color.GRAY;

    private final Context mCtx;
    private final ILightController mLightController;

    // Commands
    private static final String COMMAND_BRIGHTER = "brighter";
    private static final String COMMAND_DIMMER = "dimmer";
    private static final String COMMAND_TURN_OFF = "turn off";
    private static final String COMMAND_TURN_ON = "turn on";
    private static final String COMMAND_PERCENT = "percent";

    private static final int BRIGHTNESS_STEP = 5;

    public interface ILightController {
        public interface OnConnectionChangedListener {
            public void onConnectionChanged(int lightsConnected, boolean justConnected);
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

    public LightsSpeechCategory(Context ctx, ICategoryPresenter presenter, ILightController controller) {
        super(presenter, ctx.getString(R.string.command_lights),
                ctx.getString(R.string.assets_lights_grammer_filename),
                SpeechModel.DEFAULT,
                ctx.getString(R.string.prompt_adjust_lights));
        mCtx = ctx;
        mLightController = controller;

        mLightController.connect();
    }

    @Override
    public int getMainResDrawable() {
        return R.drawable.light_bulb_on;
    }

    @Override
    public int getBackResDrawable() {
        return R.drawable.light_bulb_off;
    }

    @Override
    public float getMainImageOpacity() {
        return mLightController.isOn() ? mLightController.getBrightnessPercentage() / 100.0f : 0.0f;
    }

    @Override
    public int getMainColor() {
        return blendColors(ON_COLOR, OFF_COLOR, getMainImageOpacity());
    }

    @Override
    public int getMainTextColor() {
        return blendColors(mCtx.getResources().getColor(R.color.lights_on_main_text_color),
                mCtx.getResources().getColor(R.color.lights_off_main_text_color),
                getMainImageOpacity());
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
                    mLightController.setBrightnessPercentage(brightness + BRIGHTNESS_STEP);
                } else if (result.equals(COMMAND_DIMMER)) {
                    int brightness = mLightController.getBrightnessPercentage();
                    mLightController.setBrightnessPercentage(brightness - BRIGHTNESS_STEP);
                } else if (result.endsWith(COMMAND_PERCENT)) {
                    String text = result.substring(0, result.lastIndexOf(COMMAND_PERCENT));
                    int percent = convertEnglishNumberStringToInteger(text);
                    mLightController.setBrightnessPercentage(percent);
                } else {
                    Toast.makeText(mCtx, "Light Command '" + result + "' not supported",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                updateColorForPresenter();
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return mLightController.isAvailable();
    }

    private void updateColorForPresenter() {
        ICategoryPresenter presenter = getPresenter();
        presenter.animateBackgroundColor(getMainColor());
        presenter.animateCaptionColor(getMainTextColor());
        presenter.animateMainImageOpacity(getMainImageOpacity());
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

    private static int blendColors(int color1, int color2, float ratio) {
        final float inverseRation = 1f - ratio;
        float r = (Color.red(color1) * ratio) + (Color.red(color2) * inverseRation);
        float g = (Color.green(color1) * ratio) + (Color.green(color2) * inverseRation);
        float b = (Color.blue(color1) * ratio) + (Color.blue(color2) * inverseRation);
        return Color.rgb((int) r, (int) g, (int) b);
    }
}
