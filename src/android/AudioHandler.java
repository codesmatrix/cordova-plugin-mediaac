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

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.Uri;

import java.lang.String;
import java.util.ArrayList;

import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * This class called by CordovaActivity to play and record audio.
 * The file can be local or over a network using http.
 *
 * Audio formats supported (tested):
 * 	.mp3, .wav
 *
 * Local audio files must reside in one of two places:
 * 		android_asset: 		file name must start with /android_asset/sound.mp3
 * 		sdcard:				file name is just sound.mp3
 */
public class AudioHandler extends CordovaPlugin {

    public static String TAG = "AudioHandler";
    HashMap<String, AudioPlayer> players;  // Audio player object
    ArrayList<AudioPlayer> pausedForPhone; // Audio players that were paused when phone call came in
    ArrayList<AudioPlayer> pausedForFocus; // Audio players that were paused when focus was lost
    private int origVolumeStream = -1;
    private CallbackContext messageChannel;

    /**
     * Constructor.
     */
    public AudioHandler() {
        this.players = new HashMap<String, AudioPlayer>();
        this.pausedForPhone = new ArrayList<AudioPlayer>();
        this.pausedForFocus = new ArrayList<AudioPlayer>();

        //Register icy protocol
        try {
            java.net.URL.setURLStreamHandlerFactory( new java.net.URLStreamHandlerFactory(){
                public java.net.URLStreamHandler createURLStreamHandler( String protocol ) {
                    LOG.d( "Registrando icy", "Asking for stream handler for protocol: '" + protocol + "'" );
                    if ("icy".equals( protocol )) return new com.spoledge.aacdecoder.IcyURLStreamHandler();
                    return null;
                }
            });
        }
        catch (Throwable t) {
            LOG.w( "Registrando icy", "Cannot set the ICY URLStreamHandler - maybe already set ? - " + t );
        }

    }

    /**
     * Executes the request and returns PluginResult.
     * @param action 		The action to execute.
     * @param args 			JSONArry of arguments for the plugin.
     * @param callbackContext		The callback context used when calling back into JavaScript.
     * @return 				A PluginResult object with a status and message.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        CordovaResourceApi resourceApi = webView.getResourceApi();
        PluginResult.Status status = PluginResult.Status.OK;
        String result = "";

        if (action.equals("startPlayingAudio")) {
            String target = args.getString(1);
            String fileUriStr;
            try {
                Uri targetUri = resourceApi.remapUri(Uri.parse(target));
                fileUriStr = targetUri.toString();
            } catch (IllegalArgumentException e) {
                fileUriStr = target;
            }
            this.startPlayingAudio(args.getString(0), FileHelper.stripFileProtocol(fileUriStr));
        }
        else if (action.equals("pausePlayingAudio")) {
            this.pausePlayingAudio(args.getString(0));
        }
        else if (action.equals("stopPlayingAudio")) {
            this.stopPlayingAudio(args.getString(0));
        } else if (action.equals("create")) {
            String id = args.getString(0);
            String src = FileHelper.stripFileProtocol(args.getString(1));
            getOrCreatePlayer(id, src);
        }
        else if (action.equals("release")) {
            boolean b = this.release(args.getString(0));
            callbackContext.sendPluginResult(new PluginResult(status, b));
            return true;
        }
        else if (action.equals("messageChannel")) {
            messageChannel = callbackContext;
            return true;
        }
        else { // Unrecognized action.
            return false;
        }

        callbackContext.sendPluginResult(new PluginResult(status, result));

        return true;
    }

    /**
     * Stop all audio players and recorders.
     */
    public void onDestroy() {
        if (!players.isEmpty()) {
            onLastPlayerReleased();
        }
        for (AudioPlayer audio : this.players.values()) {
            audio.destroy();
        }
        this.players.clear();
    }

    /**
     * Stop all audio players and recorders on navigate.
     */
    @Override
    public void onReset() {
        onDestroy();
    }

    /**
     * Called when a message is sent to plugin.
     *
     * @param id            The message id
     * @param data          The message data
     * @return              Object to stop propagation or null
     */
    public Object onMessage(String id, Object data) {

        // If phone message
        if (id.equals("telephone")) {

            // If phone ringing, then pause playing
            if ("ringing".equals(data) || "offhook".equals(data)) {

                // Get all audio players and pause them
                for (AudioPlayer audio : this.players.values()) {
                    if (audio.getState() == AudioPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                        this.pausedForPhone.add(audio);
                        audio.stopPlaying();
                    }
                }

            }

            // If phone idle, then resume playing those players we paused
            else if ("idle".equals(data)) {
                for (AudioPlayer audio : this.pausedForPhone) {
                    audio.startPlaying(null);
                }
                this.pausedForPhone.clear();
            }
        }
        return null;
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    private AudioPlayer getOrCreatePlayer(String id, String file) {
        AudioPlayer ret = players.get(id);
        if (ret == null) {
            if (players.isEmpty()) {
                onFirstPlayerCreated();
            }
            ret = new AudioPlayer(this, id, file);
            players.put(id, ret);
        }
        return ret;
    }

    /**
     * Release the audio player instance to save memory.
     * @param id				The id of the audio player
     */
    private boolean release(String id) {
        AudioPlayer audio = players.remove(id);
        if (audio == null) {
            return false;
        }
        if (players.isEmpty()) {
            onLastPlayerReleased();
        }
        audio.destroy();
        return true;
    }

    /**
     * Start or resume playing audio file.
     * @param id				The id of the audio player
     * @param file				The name of the audio file.
     */
    public void startPlayingAudio(String id, String file) {
        AudioPlayer audio = getOrCreatePlayer(id, file);
        audio.startPlaying(file);
        getAudioFocus();
    }

    /**
     * Pause playing.
     * @param id				The id of the audio player
     */
    public void pausePlayingAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.stopPlaying();
        }
    }

    /**
     * Stop playing the audio file.
     * @param id				The id of the audio player
     */
    public void stopPlayingAudio(String id) {
        AudioPlayer audio = this.players.get(id);
        if (audio != null) {
            audio.stopPlaying();
        }
    }

    /**
     * Set the audio device to be used for playback.
     *
     * @param output			1=earpiece, 2=speaker
     */
    @SuppressWarnings("deprecation")
    public void setAudioOutputDevice(int output) {
        String TAG1 = "AudioHandler.setAudioOutputDevice(): Error : ";

        AudioManager audiMgr = (AudioManager) this.cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        if (output == 2) {
            audiMgr.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_SPEAKER, AudioManager.ROUTE_ALL);
        }
        else if (output == 1) {
            audiMgr.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
        }
        else {
             LOG.e(TAG1," Unknown output device");
        }
    }

    public void pauseAllLostFocus() {
        for (AudioPlayer audio : this.players.values()) {
            if (audio.getState() == AudioPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                this.pausedForFocus.add(audio);
                audio.stopPlaying();
            }
        }
    }

    public void resumeAllGainedFocus() {
        for (AudioPlayer audio : this.pausedForFocus) {
            audio.startPlaying(null);
        }
        this.pausedForFocus.clear();
    }

    /**
     * Get the the audio focus
     */
    private OnAudioFocusChangeListener focusChangeListener = new OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
                switch (focusChange) {
                case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) :
                case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) :
                case (AudioManager.AUDIOFOCUS_LOSS) :
                    pauseAllLostFocus();
                    break;
                case (AudioManager.AUDIOFOCUS_GAIN):
                    resumeAllGainedFocus();
                    break;
                default:
                    break;
                }
            }
        };

    public void getAudioFocus() {
        String TAG2 = "AudioHandler.getAudioFocus(): Error : ";

        AudioManager am = (AudioManager) this.cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        int result = am.requestAudioFocus(focusChangeListener,
                                          AudioManager.STREAM_MUSIC,
                                          AudioManager.AUDIOFOCUS_GAIN);

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            LOG.e(TAG2,result + " instead of " + AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        }

    }


    /**
     * Get the audio device to be used for playback.
     *
     * @return					1=earpiece, 2=speaker
     */
    @SuppressWarnings("deprecation")
    public int getAudioOutputDevice() {
        AudioManager audiMgr = (AudioManager) this.cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        if (audiMgr.getRouting(AudioManager.MODE_NORMAL) == AudioManager.ROUTE_EARPIECE) {
            return 1;
        }
        else if (audiMgr.getRouting(AudioManager.MODE_NORMAL) == AudioManager.ROUTE_SPEAKER) {
            return 2;
        }
        else {
            return -1;
        }
    }

    private void onFirstPlayerCreated() {
        origVolumeStream = cordova.getActivity().getVolumeControlStream();
        cordova.getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    private void onLastPlayerReleased() {
        if (origVolumeStream != -1) {
            cordova.getActivity().setVolumeControlStream(origVolumeStream);
            origVolumeStream = -1;
        }
    }

    void sendEventMessage(String action, JSONObject actionData) {
        JSONObject message = new JSONObject();
        try {
            message.put("action", action);
            if (actionData != null) {
                message.put(action, actionData);
            }
        } catch (JSONException e) {
            LOG.e(TAG, "Failed to create event message", e);
        }

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, message);
        pluginResult.setKeepCallback(true);
        if (messageChannel != null) {
            messageChannel.sendPluginResult(pluginResult);
        }
    }
}
