package ru.kit;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Kit on 17.02.2017.
 */
class SoundManagerImpl implements SoundManager {
    private static Logger logger = Logger.getLogger(SoundManagerImpl.class.getName());

    private ExecutorService soundPool;
    private volatile Map<SoundType,Queue<Sound>> queueMap = new HashMap<>();
    private AudioClip voiceCurrentSound, functionalCurrentSound, backgroundSimpleCurrentSound;
    private MediaPlayer backgroundCurrentSound;
    private Service<Void> checkQueueService;
    private Map<Integer,Timeline> timelineMap = new HashMap<>();
    private static int idCounter = 0;
    private double[] volumes = {1,1,1,1};

    SoundManagerImpl() {
        queueMap.put(SoundType.BACKGROUND,new LinkedList<>());
        queueMap.put(SoundType.VOICE,new LinkedList<>());
        queueMap.put(SoundType.FUNCTIONAL,new LinkedList<>());
        queueMap.put(SoundType.BACKGROUND_SIMPLE,new LinkedList<>());
        checkQueueService = new Service<Void>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<Void>(){
                    @Override
                    protected Void call() throws Exception {
                        logger.info("checkQueueTask - started");
                        do {
                            for (SoundType soundType : SoundType.values()){
                                if(isAnyOnQueue(soundType)){
                                    if(!currentlyPlaying(soundType)){
                                        Sound sound = queueMap.get(soundType).remove();
                                        if(sound.isNodes()){
                                            play(sound.soundPath,sound.soundType,sound.getNodes());
                                        }else {
                                            play(sound.soundPath,sound.soundType);
                                        }
                                    }
                                }
                            }
                            Thread.sleep(20);
                        }while (!isQueueMapEmpty());
                        logger.info("checkQueueTask - ended");
                        return null;
                    }
                };
            }
        };

    }
    private boolean isQueueMapEmpty(){
        for (SoundType soundType : SoundType.values()){
            if(isAnyOnQueue(soundType)) return false;
        }
        return true;
    }

    @Override
    public void playSound(String soundPath, SoundType soundType, Node... nodes) {
        stop(soundType);
        if(nodes.length==0){
            pushSoundToTrackQueue(soundPath,soundType);
        }else {
            pushSoundToTrackQueue(soundPath,soundType,nodes);
        }

    }

    @Override
    public void playSoundWithDelay(String soundPath, SoundType soundType, long millis, Node... nodes) {

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(millis)));
        logger.info("Sound: " +soundPath+ " added to Timeline on track - " + soundType+ " with delay: " + millis + " ms");
        int index = idCounter++;
        timelineMap.put(index,timeline);
        timeline.setOnFinished(ae -> {
            timelineMap.remove(index);
            logger.info("Sound: " +soundPath+ " removed from Timeline, track: " + soundType);
            playSound(soundPath, soundType, nodes);
        });
        timeline.play();

    }

    private void play(String soundPath, SoundType soundType){
        initSoundPool();
        switch (soundType) {
            case BACKGROUND:
                //Если играет трек, стоп и освободить ресурсы
                if (backgroundCurrentSound != null && !backgroundCurrentSound.getStatus().equals(MediaPlayer.Status.DISPOSED)) {
                    backgroundCurrentSound.dispose();
                }
                Media sound = new Media(new File(soundPath).toURI().toString());
                backgroundCurrentSound = new MediaPlayer(sound);
                backgroundCurrentSound.setOnEndOfMedia(() -> {
                    if(backgroundCurrentSound!=null) backgroundCurrentSound.stop();
                });
                soundPool.execute(() -> {
                    backgroundCurrentSound.setVolume(volumes[soundType.ordinal()]);
                    backgroundCurrentSound.play();
                });
                break;

            case FUNCTIONAL:
                if (functionalCurrentSound != null) functionalCurrentSound.stop();
                functionalCurrentSound = new AudioClip(new File(soundPath).toURI().toString());
                soundPool.execute(() -> {
                    functionalCurrentSound.setVolume(volumes[soundType.ordinal()]);
                    functionalCurrentSound.play();
                });
                break;
            case VOICE:
                if (voiceCurrentSound != null) voiceCurrentSound.stop();
                voiceCurrentSound = new AudioClip(new File(soundPath).toURI().toString());
                soundPool.execute(() -> {
                    voiceCurrentSound.setVolume(volumes[soundType.ordinal()]);
                    voiceCurrentSound.play();
                });
                break;
            case BACKGROUND_SIMPLE:
                if (backgroundSimpleCurrentSound != null) backgroundSimpleCurrentSound.stop();
                backgroundSimpleCurrentSound = new AudioClip(new File(soundPath).toURI().toString());
                soundPool.execute(() -> {
                    backgroundSimpleCurrentSound.setVolume(volumes[soundType.ordinal()]);
                    backgroundSimpleCurrentSound.play();
                });
                break;
        }
        logger.info("Starting to play sound: " + soundPath + " with type: " + soundType.toString());
    }
    private void play(String soundPath, SoundType soundType, Node... nodes){
        initSoundPool();
        soundPool.execute(() -> {
            logger.info("Blocking sound " + soundType + " - LOCKED");
            play(soundPath, soundType);
            try {
                while (currentlyPlaying(soundType)) {
                    for (Node node : nodes) {
                        node.setDisable(true);
                    }
                    Thread.sleep(500);
                }

            } catch (InterruptedException e) {
                stop(soundType);
                logger.log(Level.WARNING,"Blocking sound: " + soundPath + " with type: " + soundType + " - stopped");
            } finally {
                for (Node node : nodes) {
                    node.setDisable(false);
                }
                logger.info("Blocking sound "+ soundType+" - UNLOCKED");
            }
        });
    }

    private void initSoundPool() {
        if (soundPool == null || soundPool.isShutdown()) {
            soundPool = Executors.newFixedThreadPool(SoundType.values().length);//Количество потоков = количество типов дорожек
            logger.info("SoundManager: Executor Service created with " + SoundType.values().length + " slots");
        }
    }

    @Override
    public void stop(SoundType soundType) {
        emptyTrackQueue(soundType);
        switch (soundType) {
            case BACKGROUND:
                if (backgroundCurrentSound != null && !backgroundCurrentSound.getStatus().equals(MediaPlayer.Status.DISPOSED)){
                    backgroundCurrentSound.dispose();
                    logger.log(Level.WARNING,"BACKGROUND sound has stopped");
                }
                break;
            case FUNCTIONAL:
                if (functionalCurrentSound != null){
                    functionalCurrentSound.stop();
                    logger.log(Level.WARNING,"FUNCTIONAL sound has stopped");
                }
                break;
            case VOICE:
                if (voiceCurrentSound != null){
                    voiceCurrentSound.stop();
                    logger.log(Level.WARNING,"VOICE sound has stopped");
                }
                break;
            case BACKGROUND_SIMPLE:
                if (backgroundSimpleCurrentSound != null){
                    backgroundSimpleCurrentSound.stop();
                    logger.log(Level.WARNING,"BACKGROUND_SIMPLE sound has stopped");
                }
                break;
        }
    }

    @Override
    public void stopAllSounds() {
        for (Timeline timeline :timelineMap.values()) {
            if(timeline!=null) timeline.stop();
        }
        for (SoundType soundType : SoundType.values()){
            emptyTrackQueue(soundType);
        }
        if (functionalCurrentSound != null) functionalCurrentSound.stop();
        if (voiceCurrentSound != null) voiceCurrentSound.stop();
        if (backgroundSimpleCurrentSound != null) backgroundSimpleCurrentSound.stop();
        if (backgroundCurrentSound != null && !backgroundCurrentSound.getStatus().equals(MediaPlayer.Status.DISPOSED)) {
            backgroundCurrentSound.dispose();
        }
    }

    @Override
    public void disposeAllSounds() {
        stopAllSounds();
        resetVolume();
        backgroundCurrentSound = null;
        backgroundSimpleCurrentSound = null;
        functionalCurrentSound = null;
        voiceCurrentSound = null;
        if (soundPool != null) soundPool.shutdown();
    }


    @Override
    public boolean isPlaying(SoundType soundType) {
        if (!queueMap.get(soundType).isEmpty()) return true;
        boolean isPlaying = currentlyPlaying(soundType);
        logger.info(soundType.toString() + " sound " + (isPlaying ? "Active" : "Not Active"));
        return isPlaying;
    }

    /**
     * Helpful method, carry checking logic.
     * Коряво работает, если вызвать сразу после запуска playSound то вернет false, нужно разогреть Thread.sleep-ом в 20-50 мс
     *
     * @param soundType
     * @return
     */
    //TOO BAD  TODO Доделать нормально, сейчас вызов сразу после запуска возвращает false, нужно ждать 20-50 сек чтобы вернул true!?
    private boolean currentlyPlaying(SoundType soundType) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        switch (soundType) {
            case BACKGROUND:
                if ((backgroundCurrentSound != null && !backgroundCurrentSound.getStatus().equals(MediaPlayer.Status.DISPOSED))) {
                    return backgroundCurrentSound.getStatus().equals(MediaPlayer.Status.PLAYING);
                }
                break;
            case FUNCTIONAL:
                if (functionalCurrentSound != null) {
                    return functionalCurrentSound.isPlaying();
                }
                break;
            case VOICE:
                if (voiceCurrentSound != null) {
                    return voiceCurrentSound.isPlaying();
                }
                break;
            case BACKGROUND_SIMPLE:
                if (backgroundSimpleCurrentSound != null) {
                    return backgroundSimpleCurrentSound.isPlaying();
                }
        }
        return false;
    }



    /**
     * Sound stands to the queue if track of this sound type busy right now.
     * After
     * Otherwise, sound starts to play.
     * @param soundPath
     * @param soundType
     * @param nodes
     */
    @Override
    public void pushSoundToTrackQueue(String soundPath, SoundType soundType, Node... nodes) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Not on JavaFX thread. Sounds should be played on JavaFX thread");
        queueMap.get(soundType).add(new Sound(soundPath,soundType,nodes));
        if(!checkQueueService.isRunning()){
            checkQueueService.reset();
            checkQueueService.start();
        }
        logger.info("Sound: " + soundPath + " added to " + soundType.toString() + " queue");
    }

    @Override
    public void pushSoundToTrackQueueWithDelay(String soundPath, SoundType soundType, long millis, Node... nodes) {

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(millis)));
        logger.info("Sound: " +soundPath+ " added to Timeline on track - " + soundType+ " with delay: " + millis + " ms");
        int index = idCounter++;
        timelineMap.put(index,timeline);
        timeline.setOnFinished(ae -> {
            timelineMap.remove(index);
            logger.info("Sound: " +soundPath+ " removed from Timeline, track: " + soundType);
            pushSoundToTrackQueue(soundPath, soundType, nodes);
        });
        timeline.play();
    }

    @Override
    public void emptyTrackQueue(SoundType soundType) {
        queueMap.get(soundType).clear();
        logger.info(soundType.toString() + " track queue - cleared");
    }

    /**
     *
     * @param soundType
     * @return <p><b>true</b> if any sound on queue</p>
     * <p><b>false</b> if queue is empty</p>
     */
    @Override
    public boolean isAnyOnQueue(SoundType soundType) {
        return queueMap.get(soundType).size()!=0;
    }

    @Override
    public void setVolume(SoundType soundType, double value) {
        if(value>1) value = 1;
        else if(value<0) value = 0;

        volumes[soundType.ordinal()] = value;

        if(currentlyPlaying(soundType)){
            switch (soundType) {
                case BACKGROUND:
                    backgroundCurrentSound.setVolume(value);
                    break;
                case FUNCTIONAL:

                    functionalCurrentSound.setVolume(value);
                    break;
                case VOICE:

                    voiceCurrentSound.setVolume(value);
                    break;

                case BACKGROUND_SIMPLE:
                    backgroundSimpleCurrentSound.setVolume(value);
                    break;
            }
        }
    }

    @Override
    public double getVolume(SoundType soundType) {
        return volumes[soundType.ordinal()];
    }

    @Override
    public void resetVolume() {
        for (int i = 0; i < volumes.length; i++) {
            volumes[i] = 1;
        }
    }

    private static class Sound{
        private String soundPath;
        private SoundType soundType;
        private Node[] nodes;

        public Sound(String soundPath, SoundType soundType, Node... nodes) {
            this.soundPath = soundPath;
            this.soundType = soundType;
            this.nodes = nodes;
        }

        public String getSoundPath() {
            return soundPath;
        }

        public void setSoundPath(String soundPath) {
            this.soundPath = soundPath;
        }

        public SoundType getSoundType() {
            return soundType;
        }

        public void setSoundType(SoundType soundType) {
            this.soundType = soundType;
        }

        public boolean isNodes(){
            return nodes.length != 0;
        }

        public Node[] getNodes() {
            return nodes;
        }

        public void setNodes(Node[] nodes) {
            this.nodes = nodes;
        }
    }
}
