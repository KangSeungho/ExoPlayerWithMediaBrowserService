package com.sosocom.exoplayerwithmediabrowserservice

import android.content.ComponentName
import android.media.AudioManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.ImageView

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "EPMB_MAIN"
    }

    private lateinit var mediaBrowser: MediaBrowserCompat

    private lateinit var btnPlayPause: ImageView

    private val connectionCallbacks = object: MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            Log.d(TAG, "connectionCallback onConnected")
            mediaBrowser.sessionToken.also { token ->
                val mediaController = MediaControllerCompat(
                    this@MainActivity,
                    token
                )

                MediaControllerCompat.setMediaController(this@MainActivity, mediaController)

                buildTransportControls()
            }
        }

        override fun onConnectionSuspended() {
            Log.d(TAG, "connectionCallback onConnectionSuspended")
        }

        override fun onConnectionFailed() {
            Log.d(TAG, "connectionCallback onConnectionFailed")
        }
    }

    private val controllerCallback = object: MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            Log.d(TAG, "controllerCallback onMetadataChanged metadata = $metadata")
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            Log.d(TAG, "controllerCallback onPlaybackStateChanged state = $state")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate")

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MediaPlaybackService::class.java),
            connectionCallbacks,
            null
        )
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
        Log.d(TAG, "onStart and connect")
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
        Log.d(TAG, "onResume")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop and disconnect")
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
    }

    private fun buildTransportControls() {
        val mediaController = MediaControllerCompat.getMediaController(this)

        btnPlayPause = findViewById<ImageView?>(R.id.play_pause).apply {
            setOnClickListener {
                val pbState = mediaController.playbackState.state
                if(pbState == PlaybackStateCompat.STATE_PLAYING) {
                    mediaController.transportControls.pause()
                } else {
                    mediaController.transportControls.play()
                }
            }
        }

        val metadata = mediaController.metadata
        val pbState = mediaController.playbackState

        mediaController.registerCallback(controllerCallback)
    }
}