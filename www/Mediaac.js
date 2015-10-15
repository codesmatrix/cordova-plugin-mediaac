/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
*/

var argscheck = require('cordova/argscheck'),
    utils = require('cordova/utils'),
    exec = require('cordova/exec');

var mediaObjects = {};

/**
 * This class provides access to the device media, interfaces to both sound and video
 *
 * @constructor
 * @param src                   The file name or url to play
 * @param successCallback       The callback to be called when the file is done playing or recording.
 *                                  successCallback()
 * @param errorCallback         The callback to be called if there is an error.
 *                                  errorCallback(int errorCode) - OPTIONAL
 * @param statusCallback        The callback to be called when mediaac status has changed.
 *                                  statusCallback(int statusCode) - OPTIONAL
 * @param playerType            The mediaac player type { androidPlayer | streamPlayer }.
 */
var Mediaac = function(src, successCallback, errorCallback, statusCallback, playerType) {
    argscheck.checkArgs('sFFF', 'Mediaac', arguments);

    this.id = utils.createUUID();
    mediaObjects[this.id] = this;
    this.src = src;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
    this.statusCallback = statusCallback;
    this._duration = -1;
    this._position = -1;

    this.playerType = playerType ? playerType : "androidPlayer";

    exec(null, this.errorCallback, "Mediaac", "create", [this.id, this.playerType, this.src]);
};

// Mediaac messages
Mediaac.MEDIA_STATE = 1;
Mediaac.MEDIA_DURATION = 2;
Mediaac.MEDIA_POSITION = 3;
Mediaac.MEDIA_ERROR = 9;

// Mediaac states
Mediaac.MEDIA_NONE = 0;
Mediaac.MEDIA_STARTING = 1;
Mediaac.MEDIA_RUNNING = 2;
Mediaac.MEDIA_PAUSED = 3;
Mediaac.MEDIA_STOPPED = 4;
Mediaac.MEDIA_MSG = ["None", "Starting", "Running", "Paused", "Stopped"];

//Mediaac players
Mediaac.PLAYER_ANDROID = "androidPlayer";
Mediaac.PLAYER_STREAM = "streamPlayer";

// "static" function to return existing objs.
Mediaac.get = function(id) {
    return mediaObjects[id];
};

/**
 * Start or resume playing audio file.
 */
Mediaac.prototype.play = function(options) {
    exec(null, null, "Mediaac", "startPlayingAudio", [this.id, this.playerType, this.src, options]);
};

/**
 * Stop playing audio file.
 */
Mediaac.prototype.stop = function() {
    var me = this;
    exec(function() {
        me._position = 0;
    }, this.errorCallback, "Mediaac", "stopPlayingAudio", [this.id, this.playerType]);
};

/**
 * Seek or jump to a new time in the track..
 */
Mediaac.prototype.seekTo = function(milliseconds) {
    var me = this;
    exec(function(p) {
        me._position = p;
    }, this.errorCallback, "Mediaac", "seekToAudio", [this.id, milliseconds]);
};

/**
 * Pause playing audio file.
 */
Mediaac.prototype.pause = function() {
    exec(null, this.errorCallback, "Mediaac", "pausePlayingAudio", [this.id]);
};

/**
 * Get duration of an audio file.
 * The duration is only set for audio that is playing, paused or stopped.
 *
 * @return      duration or -1 if not known.
 */
Mediaac.prototype.getDuration = function() {
    return this._duration;
};

/**
 * Get position of audio.
 */
Mediaac.prototype.getCurrentPosition = function(success, fail) {
    var me = this;
    exec(function(p) {
        me._position = p;
        success(p);
    }, fail, "Mediaac", "getCurrentPositionAudio", [this.id]);
};

/**
 * Start recording audio file.
 */
Mediaac.prototype.startRecord = function() {
    exec(null, this.errorCallback, "Mediaac", "startRecordingAudio", [this.id, this.src]);
};

/**
 * Stop recording audio file.
 */
Mediaac.prototype.stopRecord = function() {
    exec(null, this.errorCallback, "Mediaac", "stopRecordingAudio", [this.id]);
};

/**
 * Release the resources.
 */
Mediaac.prototype.release = function() {
    exec(null, this.errorCallback, "Mediaac", "release", [this.id, this.playerType]);
};

/**
 * Adjust the volume.
 */
Mediaac.prototype.setVolume = function(volume) {
    exec(null, null, "Mediaac", "setVolume", [this.id, this.playerType, volume]);
};

/**
 * Audio has status update.
 * PRIVATE
 *
 * @param id            The mediaac object id (string)
 * @param msgType       The 'type' of update this is
 * @param value         Use of value is determined by the msgType
 */
Mediaac.onStatus = function(id, msgType, value) {

    var media = mediaObjects[id];

    if(media) {
        switch(msgType) {
            case Mediaac.MEDIA_STATE :
                media.statusCallback && media.statusCallback(value);
                if(value == Mediaac.MEDIA_STOPPED) {
                    media.successCallback && media.successCallback();
                }
                break;
            case Mediaac.MEDIA_DURATION :
                media._duration = value;
                break;
            case Mediaac.MEDIA_ERROR :
                media.errorCallback && media.errorCallback(value);
                break;
            case Mediaac.MEDIA_POSITION :
                media._position = Number(value);
                break;
            default :
                console.error && console.error("Unhandled Mediaac.onStatus :: " + msgType);
                break;
        }
    }
    else {
         console.error && console.error("Received Mediaac.onStatus callback for unknown media :: " + id);
    }

};

module.exports = Mediaac;

function onMessageFromNative(msg) {
    if (msg.action == 'status') {
        Mediaac.onStatus(msg.status.id, msg.status.msgType, msg.status.value);
    } else {
        throw new Error('Unknown mediaac action' + msg.action);
    }
}

if (cordova.platformId === 'android' || cordova.platformId === 'amazon-fireos' || cordova.platformId === 'windowsphone') {

    var channel = require('cordova/channel');

    channel.createSticky('onMediaPluginReady');
    channel.waitForInitialization('onMediaPluginReady');

    channel.onCordovaReady.subscribe(function() {
        exec(onMessageFromNative, undefined, 'Mediaac', 'messageChannel', []);
        channel.initializationComplete('onMediaPluginReady');
    });
}
