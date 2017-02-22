package ru.kit;

/**
 * Created by Kit on 17.02.2017.
 */
public class SoundManagerSingleton {
    private static volatile SoundManager soundManager;

    public static SoundManager getInstance() {
        SoundManager localInstance = soundManager;
        if (localInstance == null) {
            synchronized (SoundManager.class) {
                localInstance = soundManager;
                if (localInstance == null) {
                    soundManager = localInstance = new SoundManagerImpl();
                }
            }
        }
        return localInstance;
    }
}
