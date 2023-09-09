package com.sosocom.exoplayerwithmediabrowserservice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Build
import android.os.Bundle
import android.service.media.MediaBrowserService
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver

class MediaPlaybackService: MediaBrowserServiceCompat() {
    companion object {
        private const val TAG = "EPMB_SERVICE"

        private const val MY_MEDIA_ROOT_ID = "media_root_id"
        private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
    }

    private lateinit var afChangeListener: AudioManager.OnAudioFocusChangeListener
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder

    private lateinit var audioFocusRequest: AudioFocusRequest

    val focusLock = Any()

    var playbackDelayed = false
    var playbackNowAuthorized = false

    private val mediaSessionCallback = object: MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d(TAG, "mediaSessionCallback onPlay")
            val am = baseContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val result: Int
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                    setOnAudioFocusChangeListener(afChangeListener)
                    setAudioAttributes(AudioAttributes.Builder().run {
                        setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        build()
                    })
                    setAcceptsDelayedFocusGain(true)
                    build()
                }

                result = am.requestAudioFocus(audioFocusRequest)
            } else {
                result = am.requestAudioFocus(
                    afChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }

            synchronized(focusLock) {
                playbackNowAuthorized = when(result) {
                    AudioManager.AUDIOFOCUS_REQUEST_FAILED -> false
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                        startService(Intent(baseContext, MediaBrowserService::class.java))
                        mediaSession.isActive = true
                        // 플레이어 시작
                        true
                    }
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                        playbackDelayed = true
                        false
                    }
                    else -> false
                }
            }
        }

        override fun onStop() {
            Log.d(TAG, "mediaSessionCallback onStop")
            val am = baseContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                am.abandonAudioFocusRequest(audioFocusRequest)
            }
            mediaSession.isActive = false
            // 플레이어 stop
        }

        override fun onPause() {
            Log.d(TAG, "mediaSessionCallback onPause")
            val am = baseContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // 플레이어 pause
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        mediaSession = MediaSessionCompat(baseContext, TAG).apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            setPlaybackState(stateBuilder.build())

            setCallback(mediaSessionCallback)

            setSessionToken(sessionToken)
        }

        afChangeListener = OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN ->{
                    if(playbackDelayed) {
                        synchronized(focusLock) {
                            playbackDelayed = false
                        }
                        // 재생
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    synchronized(focusLock) {
                        playbackDelayed = false
                    }
                    // 일시정지
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    synchronized(focusLock) {
                        playbackDelayed = false
                    }
                    // 일시정지
                }
            }
        }
        initNotification(mediaSession)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        Log.d(TAG, "onGetRoot clientPackageName = $clientPackageName, clientUid = $clientUid")
        return BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d(TAG, "onLoadChildren parendId = $parentId")
        if(MY_EMPTY_MEDIA_ROOT_ID == parentId) {
            result.sendResult(null)
            return
        }

        val mediaItems = emptyList<MediaBrowserCompat.MediaItem>()

        result.sendResult(mediaItems.toMutableList())
    }

    private fun initNotification(mediaSession: MediaSessionCompat) {
        val controller = mediaSession.controller ?: return
        val mediaMetadata = controller.metadata ?: return
        val description = mediaMetadata.description ?: return

        val builder = NotificationCompat.Builder(baseContext, "EPMB_NOTIFICATION").apply {
            setContentTitle(description.title)
            setContentText(description.subtitle)
            setSubText(description.description)
            setLargeIcon(description.iconBitmap)

            // 알림창을 클릭하면 플레이어를 시작 가능
            setContentIntent(controller.sessionActivity)

            setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    baseContext,
                    PlaybackStateCompat.ACTION_STOP
                )
            )

            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            setSmallIcon(R.drawable.ic_launcher_foreground)
            color = ContextCompat.getColor(baseContext, androidx.appcompat.R.color.primary_material_dark)

            addAction(
                NotificationCompat.Action(
                    R.drawable.ic_pause_24,
                    "pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        baseContext,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            )

            setStyle(MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0)
            )
        }

        startForeground(100, builder.build())
    }
}