// Type definitions for Apache Cordova Mediaac plugin
// Project: https://github.com/apache/cordova-plugin-mediaac
// Definitions by: Microsoft Open Technologies Inc <http://msopentech.com>
// Definitions: https://github.com/DefinitelyTyped/DefinitelyTyped
//
// Copyright (c) Microsoft Open Technologies Inc
// Licensed under the MIT license

declare var Mediaac: {
    new (
        src: string,
        mediaSuccess: () => void,
        mediaError?: (error: MediaError) => any,
        mediaStatus?: (status: number) => void): Mediaac;
    //Mediaac statuses
    MEDIA_NONE: number;
    MEDIA_STARTING: number;
    MEDIA_RUNNING: number;
    MEDIA_PAUSED: number;
    MEDIA_STOPPED: number
};

/**
 * This plugin provides the ability to record and play back audio files on a device.
 * NOTE: The current implementation does not adhere to a W3C specification for mediaac capture,
 * and is provided for convenience only. A future implementation will adhere to the latest
 * W3C specification and may deprecate the current APIs.
 */
interface Mediaac {
    /**
     * Starts or resumes playing an audio file.
     * @param iosPlayOptions: iOS options quirks
     */
    play(iosPlayOptions?: IosPlayOptions): void;
    /** Pauses playing an audio file. */
    pause(): void;
    /**
     * Releases the underlying operating system's audio resources. This is particularly important
     * for Android, since there are a finite amount of OpenCore instances for mediaac playback.
     * Applications should call the release function for any Mediaac resource that is no longer needed.
     */
    release(): void;
    /** Stops playing an audio file. */
    stop(): void;
}
/**
 *  iOS optional parameters for media.play
 *  See https://github.com/apache/cordova-plugin-mediaac#ios-quirks
 */
interface IosPlayOptions {
    numberOfLoops?: number;
    playAudioWhenScreenIsLocked?: boolean;
}