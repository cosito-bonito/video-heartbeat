/*
 * ADOBE SYSTEMS INCORPORATED
 * Copyright 2014 Adobe Systems Incorporated
 * All Rights Reserved.

 * NOTICE:  Adobe permits you to use, modify, and distribute this file in accordance with the
 * terms of the Adobe license agreement accompanying it.  If you have received this file from a
 * source other than Adobe, then your use, modification, or distribution of it requires the prior
 * written permission of Adobe.
 */

package com.adobe.primetime.va.samples.player;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.MediaController;

import com.adobe.primetime.va.AdBreakInfo;
import com.adobe.primetime.va.AdInfo;
import com.adobe.primetime.va.AssetType;
import com.adobe.primetime.va.ChapterInfo;
import com.adobe.primetime.va.VideoInfo;
import com.adobe.primetime.va.samples.Configuration;
import com.adobe.primetime.va.samples.R;

import java.util.Observable;

public class VideoPlayer extends Observable {
    private static final String LOG_TAG = "[VideoHeartbeatSample]::" + VideoPlayer.class.getSimpleName();

    // This sample VideoPlayer simulates a mid-roll ad at time 15:
    private static final Double AD_START_POS = 15D;
    private static final Double AD_END_POS = 30D;
    private static final Double AD_LENGTH = 15D;

    private static final Double CHAPTER1_START_POS = 0D;
    private static final Double CHAPTER1_END_POS = 15D;
    private static final Double CHAPTER1_LENGTH = 15D;

    private static final Double CHAPTER2_START_POS = 30D;
    private static final Double CHAPTER2_LENGTH = 30D;

    private static final Long MONITOR_TIMER_INTERVAL = 500L;

    private final MediaController _mediaController;
    private final ObservableVideoView _videoView;

    private Boolean _videoLoaded = false;
    private Boolean _seeking = false;
    private Boolean _buffering = false;
    private Boolean _paused = true;

    private VideoInfo _videoInfo;
    private AdBreakInfo _adBreakInfo;
    private AdInfo _adInfo;
    private ChapterInfo _chapterInfo;

    private Clock _clock;

    private final String playerName;
    private final String videoId;
    private final String streamType;

    public VideoPlayer(Activity parentActivity) {
        _videoView = (ObservableVideoView) parentActivity.findViewById(R.id.videoView);
        _videoView.setVideoPlayer(this);

        _mediaController = new MediaController(parentActivity);
        _mediaController.setMediaPlayer(_videoView);

        _videoView.setMediaController(_mediaController);
        _videoView.requestFocus();

        _videoView.setOnPreparedListener(_onPreparedListener);
        _videoView.setOnInfoListener(_onInfoListener);
        _videoView.setOnCompletionListener(_onCompletionListener);

        playerName = Configuration.PLAYER_NAME;
        videoId = Configuration.VIDEO_ID;
        streamType = AssetType.ASSET_TYPE_VOD;
    }

    public VideoInfo getVideoInfo() {
        if (_adInfo != null) { // During ad playback the main video playhead remains
                               // constant at where it was when the ad started
            _videoInfo.playhead = AD_START_POS;
        } else {
            Double vTime = getPlayhead();
            _videoInfo.playhead = (vTime < AD_START_POS) ? vTime : vTime - AD_LENGTH;
        }

        return _videoInfo;
    }

    public AdBreakInfo getAdBreakInfo() {
        return _adBreakInfo;
    }

    public AdInfo getAdInfo() {
        if (_adInfo != null) {
            _adInfo.playhead = getPlayhead() - AD_START_POS;
        }
        return _adInfo;
    }

    public ChapterInfo getChapterInfo() {
        return _chapterInfo;
    }

    public void loadContent(Uri uri) {
        if (_videoLoaded) {
            _unloadVideo();
        }
        _videoView.setVideoURI(uri);
    }

    void resumePlayback() {
        Log.d(LOG_TAG, "Resuming playback.");

        _openVideoIfNecessary();
        _paused = false;

        setChanged();
        notifyObservers(PlayerEvent.PLAY);
    }

    void pausePlayback() {
        Log.d(LOG_TAG, "Pausing playback.");

        _paused = true;

        setChanged();
        notifyObservers(PlayerEvent.PAUSE);
    }

    void seekStart() {
        Log.d(LOG_TAG, "Starting seek.");

        _openVideoIfNecessary();
        _seeking = true;

        setChanged();
        notifyObservers(PlayerEvent.SEEK_START);
    }

    private Double getDuration() {
        return (double) (_videoView.getDuration() / 1000);
    }

    private Double getPlayhead() {
        return (double) (_videoView.getCurrentPosition() / 1000);
    }

    private final MediaPlayer.OnInfoListener _onInfoListener = new MediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(MediaPlayer mediaPlayer, int what, int extra) {
            switch (what) {
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    Log.d(LOG_TAG, "#onInfo(what=MEDIA_INFO_BUFFERING_START, extra=" + extra + ")");

                    _buffering = true;

                    setChanged();
                    notifyObservers(PlayerEvent.BUFFER_START);

                    break;

                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    Log.d(LOG_TAG, "#onInfo(what=MEDIA_INFO_BUFFERING_END, extra=" + extra + ")");

                    _buffering = false;

                    setChanged();
                    notifyObservers(PlayerEvent.BUFFER_COMPLETE);

                    break;

                default:
                    Log.d(LOG_TAG, "#onInfo(what=" + what + ") - extra: " + extra);
                    break;
            }
            return true;
        }
    };

    private final MediaPlayer.OnPreparedListener _onPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mediaPlayer) {
            Log.d(LOG_TAG, "#onPrepared()");

            _mediaController.show(0);

            mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mediaPlayer) {
                    Log.d(LOG_TAG, "#onSeekComplete()");

                    _seeking = false;

                    _doPostSeekComputations();

                    setChanged();
                    notifyObservers(PlayerEvent.SEEK_COMPLETE);
                }
            });
        }
    };

    private final MediaPlayer.OnCompletionListener _onCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            Log.d(LOG_TAG, "#onCompletion()");

            _mediaController.show(0);

            _completeVideo();
        }
    };

    private void _openVideoIfNecessary() {
        if (!_videoLoaded) {
            _resetInternalState();

            _startVideo();

            _clock = new Clock();
        }
    }

    private void _completeVideo() {
        if (_videoLoaded) {
            // Complete the second chapter
            _completeChapter();

            setChanged();
            notifyObservers(PlayerEvent.COMPLETE);

            _unloadVideo();
        }
    }

    private void _unloadVideo() {
        setChanged();
        notifyObservers(PlayerEvent.VIDEO_UNLOAD);

        _clock.invalidate();

        _resetInternalState();
    }

    private void _resetInternalState() {
        _videoLoaded = false;
        _seeking = false;
        _buffering = false;
        _paused = true;
        _clock = null;
    }

    private void _startVideo() {
        // Prepare the main video info.
        _videoInfo = new VideoInfo();
        _videoInfo.id = videoId;
        _videoInfo.playerName = playerName;
        _videoInfo.length = getDuration();
        _videoInfo.streamType = streamType;
        _videoInfo.playhead = getPlayhead();

        _videoLoaded = true;

        setChanged();
        notifyObservers(PlayerEvent.VIDEO_LOAD);
    }

    private void _startChapter1() {
        // Prepare the chapter info.
        _chapterInfo = new ChapterInfo();
        _chapterInfo.length = CHAPTER1_LENGTH;
        _chapterInfo.startTime = CHAPTER1_START_POS;
        _chapterInfo.position = 1L;
        _chapterInfo.name = "First chapter";

        setChanged();
        notifyObservers(PlayerEvent.CHAPTER_START);
    }

    private void _startChapter2() {
        // Prepare the chapter info.
        _chapterInfo = new ChapterInfo();
        _chapterInfo.length = CHAPTER2_LENGTH;
        _chapterInfo.startTime = CHAPTER2_START_POS;
        _chapterInfo.position = 2L;
        _chapterInfo.name = "Second chapter";

        setChanged();
        notifyObservers(PlayerEvent.CHAPTER_START);
    }

    private void _completeChapter() {
        // Reset the chapter info.
        _chapterInfo = null;

        setChanged();
        notifyObservers(PlayerEvent.CHAPTER_COMPLETE);
    }

    private void _startAd() {
        // Prepare the ad break info.
        _adBreakInfo = new AdBreakInfo();
        _adBreakInfo.name = "First Ad-Break";
        _adBreakInfo.position = 1L;
        _adBreakInfo.playerName = playerName;
        _adBreakInfo.startTime = AD_START_POS;

        // Prepare the ad info.
        _adInfo = new AdInfo();
        _adInfo.id = "001";
        _adInfo.name = "Sample ad";
        _adInfo.length = AD_LENGTH;
        _adInfo.position = 1L;
        _adInfo.cpm = "49750702676yfh075757";
        _adInfo.playhead = getPlayhead() - AD_START_POS;

        // Start the ad.
        setChanged();
        notifyObservers(PlayerEvent.AD_START);
    }

    private void _completeAd() {
        // Complete the ad.
        setChanged();
        notifyObservers(PlayerEvent.AD_COMPLETE);

        // Clear the ad and ad-break info.
        _adInfo = null;
        _adBreakInfo = null;
    }

    private void _doPostSeekComputations() {
        Double vTime = getPlayhead();

        // Seek inside the first chapter.
        if (vTime < CHAPTER1_END_POS) {
            // If we were not inside the first chapter before, trigger a chapter start
            if (_chapterInfo == null || _chapterInfo.position != 1) {
                _startChapter1();

                // If we were in the ad, clear the ad and ad-break info, but don't send the AD_COMPLETE event.
                if (_adInfo != null) {
                    _adInfo = null;
                    _adBreakInfo = null;
                }
            }
        }

        // Seek inside the ad.
        else if (vTime >= AD_START_POS && vTime < AD_END_POS) {
            // If we were not inside the ad before, trigger an ad-start
            if (_adInfo == null) {
                _startAd();

                // Also, clear the chapter info, without sending the CHAPTER_COMPLETE event.
                _chapterInfo = null;
            }
        }

        // Seek inside the second chapter.
        else {
            // If we were not inside the 2nd chapter before, trigger a chapter start
            if (_chapterInfo == null || _chapterInfo.position != 2) {
                _startChapter2();

                // If we were in the ad, clear the ad and ad-break info, but don't send the AD_COMPLETE event.
                if (_adInfo != null) {
                    _adInfo = null;
                    _adBreakInfo = null;
                }
            }
        }
    }

    private void _onTick() {
        if (_seeking || _buffering || _paused) {
            return;
        }

        Double vTime = getPlayhead();

        // If we're inside the ad content:
        if (vTime >= AD_START_POS && vTime < AD_END_POS) {
            if (_chapterInfo != null) {
                // If we were inside a chapter, complete it.
                _completeChapter();
            }

            if (_adInfo == null) {
                // Start the ad (if not already started).
                _startAd();
            }
        }

        // Otherwise, we're outside the ad content:
        else {
            if (_adInfo != null) {
                // Complete the ad (if needed).
                _completeAd();
            }

            if (vTime < CHAPTER1_END_POS) {
                if (_chapterInfo != null && _chapterInfo.position != 1) {
                    // If we were inside another chapter, complete it.
                    _completeChapter();
                }

                if (_chapterInfo == null) {
                    // Start the first chapter.
                    _startChapter1();
                }
            } else {
                if (_chapterInfo != null && _chapterInfo.position != 2) {
                    // If we were inside another chapter, complete it.
                    _completeChapter();
                }

                if (_chapterInfo == null) {
                    // Start the second chapter.
                    _startChapter2();
                }
            }
        }
    }

    private class Clock extends HandlerThread {
        private Handler _handler;
        private Boolean _shouldStop = false;

        Clock() {
            super("VideoPlayerClock");
            start();
            Looper looper = getLooper();

            if (looper == null) {
                Log.e(LOG_TAG, "Unable to obtain looper thread.");
                return;
            }

            _handler = new Handler(getLooper());
            final Handler handler = _handler;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (!_shouldStop) {
                        _onTick();
                        handler.postDelayed(this, MONITOR_TIMER_INTERVAL);
                    }
                }
            });
        }

        public void invalidate() {
            _shouldStop = true;
        }
    }
}
