package com.matthewn4444.voiceautomation.music;

import android.content.Context;
import android.widget.Toast;

import com.matthewn4444.voiceautomation.R;
import com.matthewn4444.voiceautomation.SpeechCategory;
import com.matthewn4444.voiceautomation.SpeechController;

public class MusicSpeechCategory extends SpeechCategory {
    private static final String TAG = "MusicSpeechCategory";

    public static final String[] COMMAND_PLAY_MUSIC = {"play", "play music", "play song"};
    public static final String[] COMMAND_NEXT_SONG = {"next", "next song", "play next song"};
    public static final String[] COMMAND_PREV_SONG = {"previous", "previous song", "play previous song", "play last song"};
    public static final String[] COMMAND_PAUSE = {"pause", "pause music", "pause song"};
    public static final String[] COMMAND_VOLUME_UP = {"volume up", "louder", "increase volume"};
    public static final String[] COMMAND_VOLUME_DOWN = {"volume down", "quieter", "decrease volume"};
    public static final String[] COMMAND_SHUFFLE_ALL = {"shuffle all", "shuffle all songs"};
    public static final String[] COMMAND_SHUFFLE_ON = {"shuffle on", "enable shuffle"};
    public static final String[] COMMAND_SHUFFLE_OFF = {"shuffle off", "disable shuffle"};
    public static final String[] COMMAND_REPEAT_ON = {"repeat on", "disable repeat"};
    public static final String[] COMMAND_REPEAT_OFF = {"repeat off", "disable repeat"};
    public static final String[] COMMAND_PLAY_AGAIN = {"start over", "play again", "play song again", "play this song again"};
    public static final String COMMAND_MUTE = "mute";
    public static final String COMMAND_UNMUTE = "unmute";

    private final MusicController mController;

    public MusicSpeechCategory(Context ctx) {
        super(ctx, new MusicPresenter(ctx), ctx.getString(R.string.settings_default_activation_command_music),
                ctx.getString(R.string.settings_general_music_activation_command_key),
                ctx.getString(R.string.assets_music_grammer_filename),
                SpeechController.SpeechModel.DEFAULT,
                ctx.getString(R.string.prompt_control_music), "1e-12");
        mController = new MusicController(ctx);
    }

    @Override
    public void onResult(final String result) {
        if (result != null && isAvailable()) {
            if (matchOneOf(result, COMMAND_PLAY_MUSIC)) {
                mController.play();
            } else if (matchOneOf(result, COMMAND_PAUSE)) {
                mController.pause();
            } else if (matchOneOf(result, COMMAND_NEXT_SONG)) {
                mController.playNextSong();
            } else if (matchOneOf(result, COMMAND_PREV_SONG)) {
                mController.playPreviousSong();
            } else if (matchOneOf(result, COMMAND_VOLUME_UP)) {
                mController.increaseVolume();
            } else if (matchOneOf(result, COMMAND_VOLUME_DOWN)) {
                mController.decreaseVolume();
            } else if (matchOneOf(result, COMMAND_SHUFFLE_ALL)) {
                mController.shuffleAll();
            } else if (result.equals(COMMAND_UNMUTE)) {
                mController.setMute(false);
            } else if (result.equals(COMMAND_MUTE)) {
                mController.setMute(true);
            } else if (matchOneOf(result, COMMAND_PLAY_AGAIN)) {
                mController.seek(0);
            } else if (matchOneOf(result, COMMAND_SHUFFLE_ON)) {
                mController.setShuffle(true);
            } else if (matchOneOf(result, COMMAND_SHUFFLE_OFF)) {
                mController.setShuffle(false);
            } else if (matchOneOf(result, COMMAND_REPEAT_ON)) {
                mController.setRepeat(true);
            } else if (matchOneOf(result, COMMAND_REPEAT_OFF)) {
                mController.setRepeat(false);
            } else {
                Toast.makeText(getContext(), "Music Command '" + result + "' is not supported",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return mController.isAvailable();
    }

    private boolean matchOneOf(String text, String[] listOfWords) {
        for (int i = 0; i < listOfWords.length; i++) {
            if (text.equals(listOfWords[i])) {
                return true;
            }
        }
        return false;
    }
}
