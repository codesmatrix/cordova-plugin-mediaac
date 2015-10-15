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
import android.net.Uri;
import android.util.Log;

import java.lang.String;
import java.util.ArrayList;

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

    // AudioPlayer types
    private static String ANDROID_PLAYER = "androidPlayer";
    private static String STREAM_PLAYER   = "streamPlayer";

    public static String TAG = "AudioHandler";
    HashMap<String, AndroidPlayer>  androidPlayers;	// Android audio player object
    HashMap<String, StreamPlayer>    streamPlayers;	// Audio aac and mp3 player object
    ArrayList<AndroidPlayer>androidPlayersPausedForPhone;  // Android audio players that were paused when phone call came in
    ArrayList<StreamPlayer>  streamPlayersPausedForPhone;    // Audio aac and mp3 players that were paused when phone call came in
    private int origVolumeStream = -1;
    private CallbackContext messageChannel;

    /**
     * Constructor.
     */
    public AudioHandler() {
        this.androidPlayers = new HashMap<String, AndroidPlayer>();
        this.streamPlayers  =  new HashMap<String, StreamPlayer>();
        this.androidPlayersPausedForPhone = new ArrayList<AndroidPlayer>();
        this.streamPlayersPausedForPhone  =  new ArrayList<StreamPlayer>();

        //Register icy protocol
        try {
            java.net.URL.setURLStreamHandlerFactory( new java.net.URLStreamHandlerFactory(){
                public java.net.URLStreamHandler createURLStreamHandler( String protocol ) {
                    Log.d( "Registrando icy", "Asking for stream handler for protocol: '" + protocol + "'" );
                    if ("icy".equals( protocol )) return new com.spoledge.aacdecoder.IcyURLStreamHandler();
                    return null;
                }
            });
        }
        catch (Throwable t) {
            Log.w( "Registrando icy", "Cannot set the ICY URLStreamHandler - maybe already set ? - " + t );
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

        if (action.equals("startRecordingAudio")) {
            String target = args.getString(1);
            String fileUriStr;
            try {
                Uri targetUri = resourceApi.remapUri(Uri.parse(target));
                fileUriStr = targetUri.toString();
            } catch (IllegalArgumentException e) {
                fileUriStr = target;
            }
            // Start Recording Audio
            this.startRecordingAudio(args.getString(0), FileHelper.stripFileProtocol(fileUriStr));
        }
        else if (action.equals("stopRecordingAudio")) {
            // Stop Recording Audio
            this.stopRecordingAudio(args.getString(0));
        }
        else if (action.equals("startPlayingAudio")) {
            String target = args.getString(2);
            String fileUriStr;
            try {
                Uri targetUri = resourceApi.remapUri(Uri.parse(target));
                fileUriStr = targetUri.toString();
            } catch (IllegalArgumentException e) {
                fileUriStr = target;
            }
            // Start Playing Audio
            this.startPlayingAudio(args.getString(0), FileHelper.stripFileProtocol(fileUriStr), args.getString(1));
        }
        else if (action.equals("seekToAudio")) {
            // Seek to Audio
            this.seekToAudio(args.getString(0), args.getInt(1));
        }
        else if (action.equals("pausePlayingAudio")) {
            // Pause Playing Audio
            this.pausePlayingAudio(args.getString(0));
        }
        else if (action.equals("stopPlayingAudio")) {
            // Stop Playing Audio
            this.stopPlayingAudio(args.getString(0), args.getString(1));
        } else if (action.equals("setVolume")) {
            try {
                // Set Volume
                this.setVolume(args.getString(0), Float.parseFloat(args.getString(2)), args.getString(1));
            } catch (NumberFormatException nfe) {
                //no-op
            }
        } else if (action.equals("getCurrentPositionAudio")) {
            // Get Current Position Audio
            float f = this.getCurrentPositionAudio(args.getString(0));
            callbackContext.sendPluginResult(new PluginResult(status, f));
            return true;
        }
        else if (action.equals("getDurationAudio")) {
            // Get Duration Audio
            float f = this.getDurationAudio(args.getString(0), args.getString(2));
            callbackContext.sendPluginResult(new PluginResult(status, f));
            return true;
        }
        else if (action.equals("create")) {
            String id = args.getString(0);
            String type = args.getString(1);
            String src = FileHelper.stripFileProtocol(args.getString(2));

            if (type.equals(ANDROID_PLAYER)) {
                getOrCreateAndroidPlayer(id, src);

            } else {
                // if (type == STREAM_PLAYER)
                getOrCreateStreamPlayer(id, src);
            }

        }
        else if (action.equals("release")) {
            // Release
            boolean b = this.release(args.getString(0), args.getString(1));
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
        if (!androidPlayers.isEmpty() && !streamPlayers.isEmpty()) {
            onLastPlayerReleased();
        }

        for (AndroidPlayer audio : this.androidPlayers.values()) {
            audio.destroy();
        }
        this.androidPlayers.clear();

        for (StreamPlayer audio : this.streamPlayers.values()) {
            audio.destroy();
        }
        this.streamPlayers.clear();
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

                for (AndroidPlayer audio : this.androidPlayers.values()) {
                    if (audio.getState() == AndroidPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                        this.androidPlayersPausedForPhone.add(audio);
                        audio.stopPlaying();
                    }
                }
                for (StreamPlayer audio : this.streamPlayers.values()) {
                    if (audio.getState() == StreamPlayer.STATE.MEDIA_RUNNING.ordinal()) {
                        this.streamPlayersPausedForPhone.add(audio);
                        audio.stopPlaying();
                    }
                }

            }

            // If phone idle, then resume playing those players we paused
            else if ("idle".equals(data)) {

                for (AndroidPlayer audio : this.androidPlayersPausedForPhone) {
                    audio.startPlaying(null);
                }
                this.androidPlayersPausedForPhone.clear();

                for (StreamPlayer audio : this.streamPlayersPausedForPhone) {
                    audio.startPlaying(null);
                }
                this.streamPlayersPausedForPhone.clear();
            }
        }
        return null;
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    private AndroidPlayer getOrCreateAndroidPlayer(String id, String file) {
        AndroidPlayer ret = androidPlayers.get(id);
        if (ret == null) {
            if (androidPlayers.isEmpty()) {
                onFirstPlayerCreated();
            }
            ret = new AndroidPlayer(this, id, file);
            androidPlayers.put(id, ret);
        }
        return ret;
    }
    private StreamPlayer getOrCreateStreamPlayer(String id, String file) {
        StreamPlayer ret = streamPlayers.get(id);
        if (ret == null) {
            if (streamPlayers.isEmpty()) {
                onFirstPlayerCreated();
            }
            ret = new StreamPlayer(this, id, file);
            streamPlayers.put(id, ret);
        }
        return ret;
    }

    /**
     * Release the audio player instance to save memory.
     * @param id				The id of the audio player
     * @param type				The audio player type
     */
    private boolean release(String id, String type) {

        Player audio;

        if (type.equals(ANDROID_PLAYER)) {

            audio = androidPlayers.remove(id);
            if (audio == null) {
                return false;
            }
            if (androidPlayers.isEmpty()) {
                onLastPlayerReleased();
            }

        } else {
            // if (type == STREAM_PLAYER)

            audio = streamPlayers.remove(id);
            if (audio == null) {
                return false;
            }
            if (streamPlayers.isEmpty()) {
                onLastPlayerReleased();
            }

        }
        audio.destroy();
        return true;
    }

    /**
     * Start recording and save the specified file.
     * @param id				The id of the audio player
     * @param file				The name of the file
     */
    public void startRecordingAudio(String id, String file) {
        AndroidPlayer audio = getOrCreateAndroidPlayer(id, file);
        audio.startRecording(file);
    }

    /**
     * Stop recording and save to the file specified when recording started.
     * @param id				The id of the audio player
     */
    public void stopRecordingAudio(String id) {
        AndroidPlayer audio = this.androidPlayers.get(id);
        if (audio != null) {
            audio.stopRecording();
        }
    }

    /**
     * Start or resume playing audio file.
     * @param id				The id of the audio player
     * @param file				The name of the audio file.
     * @param type				The audio player type
     */
    public void startPlayingAudio(String id, String file, String type) {
        Player audio;

        if (type.equals(ANDROID_PLAYER)) {
                audio = getOrCreateAndroidPlayer(id, file);

        } else {
            // if (type == STREAM_PLAYER)
            audio = getOrCreateStreamPlayer(id, file);

        }
        audio.startPlaying(file);
    }

    /**
     * Seek to a location.
     * @param id				The id of the audio player
     * @param milliseconds		int: number of milliseconds to skip 1000 = 1 second
     */
    public void seekToAudio(String id, int milliseconds) {
        AndroidPlayer audio = this.androidPlayers.get(id);
        if (audio != null) {
            audio.seekToPlaying(milliseconds);
        }
    }

    /**
     * Pause playing.
     * @param id				The id of the audio player
     */
    public void pausePlayingAudio(String id) {
        AndroidPlayer audio = this.androidPlayers.get(id);
        if (audio != null) {
            audio.pausePlaying();
        }
    }

    /**
     * Stop playing the audio file.
     * @param id				The id of the audio player
     * @param type				The audio player type
     */
    public void stopPlayingAudio(String id, String type) {
        Player audio;

        if (type.equals(ANDROID_PLAYER)) {
            audio = this.androidPlayers.get(id);

        } else {
            // if (type == STREAM_PLAYER)
            audio = this.streamPlayers.get(id);

        }

        if (audio != null) {
            audio.stopPlaying();
        }
    }

    /**
     * Get current position of playback.
     * @param id				The id of the audio player
     * @return 					position in msec
     */
    public float getCurrentPositionAudio(String id) {
        AndroidPlayer audio = this.androidPlayers.get(id);
        if (audio != null) {
            return (audio.getCurrentPosition() / 1000.0f);
        }
        return -1;
    }

    /**
     * Get the duration of the audio file.
     * @param id				The id of the audio player
     * @param file				The name of the audio file.
     * @return					The duration in msec.
     */
    public float getDurationAudio(String id, String file) {
        AndroidPlayer audio = getOrCreateAndroidPlayer(id, file);
        return audio.getDuration(file);
    }

    /**
     * Set the audio device to be used for playback.
     *
     * @param output			1=earpiece, 2=speaker
     */
    @SuppressWarnings("deprecation")
    public void setAudioOutputDevice(int output) {
        AudioManager audiMgr = (AudioManager) this.cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        if (output == 2) {
            audiMgr.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_SPEAKER, AudioManager.ROUTE_ALL);
        }
        else if (output == 1) {
            audiMgr.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
        }
        else {
            System.out.println("AudioHandler.setAudioOutputDevice() Error: Unknown output device.");
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

    /**
     * Set the volume for an audio device
     *
     * @param id				The id of the audio player
     * @param volume            Volume to adjust to 0.0f - 1.0f
     * @param type				The audio player type
     */
    public void setVolume(String id, float volume, String type) {

        if (type.equals(ANDROID_PLAYER)) {
            AndroidPlayer audio = this.androidPlayers.get(id);

            if (audio != null) {
                audio.setVolume(volume);
            } else {
                System.out.println("AudioHandler.setVolume() Error: Unknown Android Player " + id);
            }

        } else {
            // if (type == STREAM_PLAYER)

            // audio = this.streamPlayers.get(id);
            System.out.println("AudioHandler.setVolume() Error: Cannot set volume to Stream Player " + id);
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
            Log.e(TAG, "Failed to create event message", e);
        }

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, message);
        pluginResult.setKeepCallback(true);
        if (messageChannel != null) {
            messageChannel.sendPluginResult(pluginResult);
        }
    }
}
