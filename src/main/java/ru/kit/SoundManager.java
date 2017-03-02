package ru.kit;

import javafx.scene.Node;

/**
 * Created by Kit on 16.02.2017.
 */
public interface SoundManager {

    void stop(SoundType soundType);

    void stopAllSounds();

    void disposeAllSounds();

    boolean isPlaying(SoundType soundType);

    void playSound(String soundPath, SoundType soundType, Node... nodes);

    void playSoundWithDelay(String soundPath, SoundType soundType, long millis, Node... nodes);

    void pushSoundToTrackQueue(String soundPath, SoundType soundType, Node... nodes);

    void pushSoundToTrackQueueWithDelay(String soundPath, SoundType soundType, long millis, Node... nodes);

    void emptyTrackQueue(SoundType soundType);

    boolean isAnyOnQueue(SoundType soundType);

    void setVolume(SoundType soundType, double value);

    double getVolume(SoundType soundType);

    void resetVolume();

    enum SoundType{
        /**
         * Background playing sound. Currently, only one sound of this type can be played.
         * To play any other background this one should be stopped. Recommended to use with medium or long clips.
         */
        BACKGROUND,
        /**
         * Voice sound is for voice clips. Currently, only one sound of this type can be played.
         * If you try to play another VOICE sound while ones playing, the first one will be stopped.
         */
        VOICE,
        /**
         * Very short sounds, playing a few seconds and not bothering another sound types.
         */
        FUNCTIONAL,
        /**
         * Background playing sound. Differs from BACKGROUND ...
         */
        BACKGROUND_SIMPLE

    }

}
