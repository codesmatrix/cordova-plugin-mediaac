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
    exec(null, this.errorCallback, "Mediaac", "create", [this.id, this.src]);
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

// "static" function to return existing objs.
Mediaac.get = function(id) {
    return mediaObjects[id];
};

/**
 * Start or resume playing audio file.
 */
Mediaac.prototype.play = function(options) {
    exec(null, null, "Mediaac", "startPlayingAudio", [this.id, this.src, options]);
};

/**
 * Stop playing audio file.
 */
Mediaac.prototype.stop = function() {
    var me = this;
    exec(function() {
        me._position = 0;
    }, this.errorCallback, "Mediaac", "stopPlayingAudio", [this.id]);
};

/**
 * Pause playing audio file.
 */
Mediaac.prototype.pause = function() {
    exec(null, this.errorCallback, "Mediaac", "pausePlayingAudio", [this.id]);
};

/**
 * Release the resources.
 */
Mediaac.prototype.release = function() {
    exec(null, this.errorCallback, "Mediaac", "release", [this.id, this.playerType]);
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

    if (media) {
        switch(msgType) {
            case Mediaac.MEDIA_STATE :
                if (media.statusCallback) {
                    media.statusCallback(value);
                }
                if(value == Mediaac.MEDIA_STOPPED) {
                    if (media.successCallback) {
                        media.successCallback();
                    }
                }
                break;
            case Mediaac.MEDIA_DURATION :
                media._duration = value;
                break;
            case Mediaac.MEDIA_ERROR :
                if (media.errorCallback) {
                    media.errorCallback(value);
                }
                break;
            case Mediaac.MEDIA_POSITION :
                media._position = Number(value);
                break;
            default :
                if (console.error) {
                    console.error("Unhandled Mediaac.onStatus :: " + msgType);
                }
                break;
        }
    } else if (console.error) {
        console.error("Received Mediaac.onStatus callback for unknown media :: " + id);
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
