
package org.apache.cordova.mediaac;


public abstract class Player {

    // AndroidPlayer modes
    public enum MODE { NONE, PLAY, RECORD };

    // AndroidPlayer states
    public enum STATE { MEDIA_NONE,
                        MEDIA_STARTING,
                        MEDIA_RUNNING,
                        MEDIA_PAUSED,
                        MEDIA_STOPPED,
                        MEDIA_LOADING
                      };

    private static final String LOG_TAG = "AndroidPlayer";

    // AndroidPlayer message ids
    private static int MEDIA_STATE = 1;
    private static int MEDIA_DURATION = 2;
    private static int MEDIA_POSITION = 3;
    private static int MEDIA_ERROR = 9;

    // Media error codes
    private static int MEDIA_ERR_NONE_ACTIVE    = 0;
    private static int MEDIA_ERR_ABORTED        = 1;
//    private static int MEDIA_ERR_NETWORK        = 2;
//    private static int MEDIA_ERR_DECODE         = 3;
//    private static int MEDIA_ERR_NONE_SUPPORTED = 4;

    private String id;                      // The id of this player (used to identify Media object in JavaScript)
    private MODE mode = MODE.NONE;          // Playback or Recording mode
    private STATE state = STATE.MEDIA_NONE; // State of recording or playback

    private String audioFile = null;        // File name to play or record to
    private float duration = -1;            // Duration of audio

    private String tempFile = null;         // Temporary recording file name

    private boolean prepareOnly = true;     // playback after file prepare flag
    private int seekOnPrepared = 0;     // seek to this location once media is prepared

    /**
     * Constructor.
     */
//    public abstract void Player() {}

    /**
     * Destroy player and stop audio playing or recording.
     */
    public abstract void destroy();

    //==========================================================================
    // Playback
    //==========================================================================

    /**
     * Start or resume playing audio file.
     */
    public abstract void startPlaying(String file);

    /**
     * Pause playing.
     */
//    public abstract void pausePlaying() {}

    /**
     * Stop playing the audio file.
     */
    public abstract void stopPlaying();


    /**
     * Set the state and send it to JavaScript.
     *
     * @param state
     */
//    private abstract void setState(STATE state) {}

    /**
     * Get the audio state.
     *
     * @return int
     */
//    public abstract int getState(){}



    /**
     * attempts to initialize the media player for playback
     */
//    private abstract void readyPlayer() {}

//    private abstract void sendErrorStatus() {}

//    private abstract void sendStatusChange() {}
}
