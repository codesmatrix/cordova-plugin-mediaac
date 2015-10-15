/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.mediaac;

import com.spoledge.aacdecoder.MultiPlayer;
import com.spoledge.aacdecoder.PlayerCallback;

import android.media.AudioTrack;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class implements the audio playback and recording capabilities used by Cordova.
 * It is called by the AudioHandler Cordova class.
 * Only one file can be played or recorded per class instance.
 *
 * Local audio files must reside in one of two places:
 *      android_asset:      file name must start with /android_asset/sound.mp3
 *      sdcard:             file name is just sound.mp3
 */
public class StreamPlayer extends Player implements PlayerCallback{

    // StreamPlayer states
    public enum STATE { MEDIA_NONE,
                        MEDIA_STARTING,
                        MEDIA_RUNNING,
                        MEDIA_PAUSED,
                        MEDIA_STOPPED,
                        MEDIA_LOADING
                      }

    private static final String LOG_TAG = "StreamPlayer";

    // StreamPlayer message ids
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

    private AudioHandler handler;           // The AudioHandler object
    private String id;                      // The id of this player (used to identify Media object in JavaScript)
    private STATE state = STATE.MEDIA_NONE; // State of recording or playback

    private String audioFile = null;        // File name to play or record to

    private MultiPlayer player = null;      // Audio player object
    private boolean prepareOnly = true;     // playback after file prepare flag

    /**
     * Constructor.
     *
     * @param handler           The audio handler object
     * @param id                The id of this audio player
     */
    public StreamPlayer(AudioHandler handler, String id, String file) {
        this.handler = handler;
        this.id = id;
        this.audioFile = file;
    }

    /**
     * Destroy player and stop audio playing or recording.
     */
    public void destroy() {
        // Stop any play or record
        if (this.player != null) {
            if ((this.state == STATE.MEDIA_RUNNING) || (this.state == STATE.MEDIA_PAUSED)) {
                this.player.stop();
            }
            this.player = null;
        }
    }

    //==========================================================================
    // Playback
    //==========================================================================

    /**
     * Start or resume playing audio file.
     *
     * @param file              The name of the audio file.
     */
    public void startPlaying(String file) {
        if (this.readyPlayer(file) && this.player != null) {
            this.player.playAsync(file);
        } else {
            this.prepareOnly = false;
        }
    }

    /**
     * Stop playing the audio file.
     */
    public void stopPlaying() {
        if ((this.state == STATE.MEDIA_RUNNING) || (this.state == STATE.MEDIA_PAUSED)) {
            if (player != null) {
                player.stop();
                Log.d(LOG_TAG, "stopPlaying is calling stopped");
                player = null;
            }
        }
        else {
            Log.d(LOG_TAG, "StreamPlayer Error: stopPlaying() called during invalid state: " + this.state.ordinal());
            sendErrorStatus(MEDIA_ERR_NONE_ACTIVE);
        }
    }

    /**
     * Callback to be invoked when the player is started.
     */
    public void playerStarted () {
        this.setState(STATE.MEDIA_RUNNING);

        // Send status notification to JavaScript
        sendStatusChange(MEDIA_DURATION, null, null);
    }

    /**
     * Determine if playback file is streaming or local.
     * It is streaming if file name starts with "http://"
     *
     * @param file              The file name
     * @return                  T=streaming, F=local
     */
    public boolean isStreaming(String file) {
        return file.contains("http://") || file.contains("https://");
    }

    /**
     * Callback to be invoked when the player is stopped.
     * Note: __after__ this method the method playerException might be also called.
     * @param perf performance indicator - how much is decoder faster than audio player in %;
     *      if less than 0, then decoding of audio is slower than needed by audio player;
     *      example: perf = 53 means that audio decoder is 53% faster than audio player
     *          (production of audio data is 1.53 faster than consuption of audio data)
     */
    public void playerStopped ( int perf ) {
        Log.d(LOG_TAG, "stopPlaying is calling stopped");
        this.setState(STATE.MEDIA_STOPPED);

        // Send status notification to JavaScript
        sendStatusChange(MEDIA_DURATION, null, null);
    }

    /**
     * This method is called when an exception is thrown by player.
     */
    public void playerException( Throwable t ) {
        Log.d(LOG_TAG, "StreamPlayer.playerException(" + t.toString() + ")");
        if (this.state == STATE.MEDIA_RUNNING) playerStopped( 0 );

        // Send error notification to JavaScript
        sendErrorStatus(t.hashCode());
    }

    public void playerPCMFeedBuffer( final boolean isPlaying,
                                     final int audioBufferSizeMs, final int audioBufferCapacityMs ) {
    }

    public void playerMetadata( final String key, final String value ) {
    }
    public void playerAudioTrackCreated( AudioTrack atrack ) {
    }

    /**
     * Set the state and send it to JavaScript.
     *
     * @param state
     */
    private void setState(STATE state) {
        if (this.state != state) {
            sendStatusChange(MEDIA_STATE, null, (float)state.ordinal());
        }
        this.state = state;
    }

    /**
     * Get the audio state.
     *
     * @return int
     */
    public int getState() {
        return this.state.ordinal();
    }

    /**
     * attempts to initialize the media player for playback
     * @param file the file to play
     * @return false if player not ready, reports if in wrong mode or state
     */
    private boolean readyPlayer(String file) {
        switch (this.state) {
            case MEDIA_NONE:
                if (this.player == null) {
                    //TODO: Agregar buffer (this, audiobuffer, decoderbuffer).
                    this.player = new MultiPlayer(this);
                    this.setState(STATE.MEDIA_STARTING);
                    return true;
                }
            case MEDIA_LOADING:
                //cordova js is not aware of MEDIA_LOADING, so we send MEDIA_STARTING instead
                Log.d(LOG_TAG, "StreamPlayer Loading: startPlaying() called during media preparation: " + STATE.MEDIA_STARTING.ordinal());
                this.prepareOnly = false;
                return false;
            case MEDIA_STARTING:
            case MEDIA_RUNNING:
            case MEDIA_PAUSED:
            case MEDIA_STOPPED:
                return true;
            default:
                Log.d(LOG_TAG, "StreamPlayer Error: startPlaying() called during invalid state: " + this.state);
                sendErrorStatus(MEDIA_ERR_ABORTED);
        }
        return false;
    }

    private void sendErrorStatus(int errorCode) {
        sendStatusChange(MEDIA_ERROR, errorCode, null);
    }

    private void sendStatusChange(int messageType, Integer additionalCode, Float value) {

        if (additionalCode != null && value != null) {
            throw new IllegalArgumentException("Only one of additionalCode or value can be specified, not both");
        }

        JSONObject statusDetails = new JSONObject();
        try {
            statusDetails.put("id", this.id);
            statusDetails.put("msgType", messageType);
            if (additionalCode != null) {
                JSONObject code = new JSONObject();
                code.put("code", additionalCode.intValue());
                statusDetails.put("value", code);
            }
            else if (value != null) {
                statusDetails.put("value", value.floatValue());
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to create status details", e);
        }

        this.handler.sendEventMessage("status", statusDetails);
    }
}
