/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.incomingcall

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import org.linphone.core.*

class IncomingCallActivity:  AppCompatActivity() {
    private lateinit var core: Core

    private val coreListener = object: CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(core: Core, account: Account, state: RegistrationState?, message: String) {
            findViewById<TextView>(R.id.registration_status).text = message
            android.util.Log.i("Sequence", "fun onAccountRegistrationStateChanged() state : $state")
            android.util.Log.i("Sequence", "fun onAccountRegistrationStateChanged() message : $message")

            if (state == RegistrationState.Failed) {
                findViewById<Button>(R.id.connect).isEnabled = true
            } else if (state == RegistrationState.Ok) {
                findViewById<LinearLayout>(R.id.register_layout).visibility = View.GONE
                findViewById<RelativeLayout>(R.id.call_layout).visibility = View.VISIBLE
            }
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            findViewById<TextView>(R.id.call_status).text = message
            android.util.Log.i("Sequence", "fun onCallStateChanged()")
            android.util.Log.i("Sequence", "fun onCallStateChanged() state : $state")

            if(state.toString() == "StreamsRunning" ){
                val call = if (core.currentCall != null) core.currentCall else core.calls[0]
                call ?: return
                val tmp = call.currentParams.videoEnabled()
                android.util.Log.i("Sequence", "fun onCallStateChanged() core.videoEnabled() : $tmp")
                if ( !call.currentParams.videoEnabled() ){
                    toggleVideo()
                }
                android.util.Log.i("Sequence", "fun onCallStateChanged() state.toString() == \"StreamsRunning\" ")

            }
            android.util.Log.i("Sequence", "fun onCallStateChanged() Call.State.IncomingReceived")
            android.util.Log.i("Sequence", "fun onCallStateChanged() Call.State.IncomingReceived call.remoteAddress.asStringUriOnly() : $call.remoteAddress.asStringUriOnly() ")
            android.util.Log.i("Sequence", "fun onCallStateChanged() Call.State.Connected")
            android.util.Log.i("Sequence", "fun onCallStateChanged() Call.State.StreamsRunning")

            // When a call is received
            when (state) {
                Call.State.IncomingReceived -> {
                    findViewById<Button>(R.id.hang_up).isEnabled = true
                    findViewById<Button>(R.id.answer).isEnabled = true
                    findViewById<EditText>(R.id.remote_address).setText(call.remoteAddress.asStringUriOnly())

                }
                Call.State.Connected -> {

                    findViewById<Button>(R.id.mute_mic).isEnabled = true
                    findViewById<Button>(R.id.toggle_speaker).isEnabled = true
                    findViewById<Button>(R.id.toggle_video).isEnabled = true
                }
                Call.State.StreamsRunning -> {
                    // This state indicates the call is active.
                    // You may reach this state multiple times, for example after a pause/resume
                    // or after the ICE negotiation completes
                    // Wait for the call to be connected before allowing a call update
                }


                Call.State.IncomingEarlyMedia -> {
                    android.util.Log.i("Sequence", "fun onCallStateChanged()  Call.State.IncomingEarlyMedia")
                }
                Call.State.UpdatedByRemote -> {
                    android.util.Log.i("Sequence", "fun onCallStateChanged()  Call.State.UpdatedByRemote")
                    if (packageManager.checkPermission(Manifest.permission.CAMERA, packageName) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.CAMERA), 0)
                        return
                    }
                }
                Call.State.Updating -> {
                    android.util.Log.i("Sequence", "fun onCallStateChanged()  Call.State.Updating")
                }
                Call.State.Released -> {
                    android.util.Log.i("Sequence", "fun onCallStateChanged() Call.State.Released")
                    findViewById<Button>(R.id.hang_up).isEnabled = false
                    findViewById<Button>(R.id.answer).isEnabled = false
                    findViewById<Button>(R.id.mute_mic).isEnabled = false
                    findViewById<Button>(R.id.toggle_speaker).isEnabled = false
                    findViewById<Button>(R.id.toggle_video).isEnabled = false
                    findViewById<EditText>(R.id.remote_address).text.clear()
                    findViewById<TextureView>(R.id.remote_video_surface).isVisible = false
                }
            }
        }

        override fun onAudioDeviceChanged(core: Core, audioDevice: AudioDevice) {
            // This callback will be triggered when a successful audio device has been changed
            android.util.Log.i("Sequence", "fun onAudioDeviceChanged()")

        }

        override fun onAudioDevicesListUpdated(core: Core) {
            // This callback will be triggered when the available devices list has changed,
            // for example after a bluetooth headset has been connected/disconnected.
            android.util.Log.i("Sequence", "fun onAudioDevicesListUpdated()")
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("Sequence", "fun onCreate()")
        setContentView(R.layout.incoming_call_activity)

        val factory = Factory.instance()
        factory.setDebugMode(true, "Hello Linphone")
        core = factory.createCore(null, null, this)

        findViewById<Button>(R.id.connect).setOnClickListener {
            login()
            it.isEnabled = false
        }

        // For video to work, we need two TextureViews:
        // one for the remote video and one for the local preview
        core.nativeVideoWindowId = findViewById(R.id.remote_video_surface)
        // The local preview is a org.linphone.mediastream.video.capture.CaptureTextureView
        // which inherits from TextureView and contains code to keep the ratio of the capture video
        core.nativePreviewWindowId = findViewById(R.id.local_preview_video_surface)

        // Here we enable the video capture & display at Core level
        // It doesn't mean calls will be made with video automatically,
        // But it allows to use it later
        core.enableVideoCapture(false)
        core.enableVideoDisplay(true)

        // When enabling the video, the remote will either automatically answer the update request
        // or it will ask it's user depending on it's policy.
        // Here we have configured the policy to always automatically accept video requests
        core.videoActivationPolicy.automaticallyAccept = true
        // If you don't want to automatically accept,
        // you'll have to use a code similar to the one in toggleVideo to answer a received request

        // If the following property is enabled, it will automatically configure created call params with video enabled
        //core.videoActivationPolicy.automaticallyInitiate = true


        findViewById<Button>(R.id.hang_up).setOnClickListener {
            // Terminates the call, whether it is ringing or running
            core.currentCall?.terminate()
        }

        findViewById<Button>(R.id.answer).setOnClickListener {
            android.util.Log.i("Sequence", "fun onCreate()  findViewById<Button>(R.id.answer).setOnClickListener")
            // if we wanted, we could create a CallParams object
            // and answer using this object to make changes to the call configuration
            // (see OutgoingCall tutorial)
            core.currentCall?.accept()
        }

        findViewById<Button>(R.id.mute_mic).setOnClickListener {
            // The following toggles the microphone, disabling completely / enabling the sound capture
            // from the device microphone
            core.enableMic(!core.micEnabled())
        }

        findViewById<Button>(R.id.toggle_speaker).setOnClickListener {
            toggleSpeaker()
        }

        findViewById<Button>(R.id.toggle_video).setOnClickListener {
            toggleVideo()
        }

        findViewById<Button>(R.id.hang_up).setOnClickListener {
            hangUp()
        }

        findViewById<Button>(R.id.hang_up).isEnabled = false
        findViewById<Button>(R.id.answer).isEnabled = false
        findViewById<Button>(R.id.mute_mic).isEnabled = false
        findViewById<Button>(R.id.toggle_speaker).isEnabled = false
        findViewById<EditText>(R.id.remote_address).isEnabled = false
        findViewById<Button>(R.id.toggle_video).isEnabled = false
    }

    private fun toggleSpeaker() {
        // Get the currently used audio device
        val currentAudioDevice = core.currentCall?.outputAudioDevice
        val speakerEnabled = currentAudioDevice?.type == AudioDevice.Type.Speaker

        // We can get a list of all available audio devices using
        // Note that on tablets for example, there may be no Earpiece device
        for (audioDevice in core.audioDevices) {
            if (speakerEnabled && audioDevice.type == AudioDevice.Type.Earpiece) {
                core.currentCall?.outputAudioDevice = audioDevice
                return
            } else if (!speakerEnabled && audioDevice.type == AudioDevice.Type.Speaker) {
                core.currentCall?.outputAudioDevice = audioDevice
                return
            }/* If we wanted to route the audio to a bluetooth headset
            else if (audioDevice.type == AudioDevice.Type.Bluetooth) {
                core.currentCall?.outputAudioDevice = audioDevice
            }*/
        }
    }

    private fun login() {
        android.util.Log.i("Sequence", "fun login()")
        val username = findViewById<EditText>(R.id.username).text.toString()
        val password = findViewById<EditText>(R.id.password).text.toString()
        val domain = findViewById<EditText>(R.id.domain).text.toString()
        android.util.Log.i("Sequence", "fun login() username : $username")
        android.util.Log.i("Sequence", "fun login() password : $password")
        android.util.Log.i("Sequence", "fun login() domain : $domain")

        val transportType = when (findViewById<RadioGroup>(R.id.transport).checkedRadioButtonId) {
            R.id.udp -> TransportType.Udp
            R.id.tcp -> TransportType.Tcp
            else -> TransportType.Tls
        }
//        android.util.Log.i("Sequence", "fun login() authInfo : $authInfo")
//        android.util.Log.i("Sequence", "fun login() identity : $identity")
//        android.util.Log.i("Sequence", "fun login() address : $address")


        val authInfo = Factory.instance().createAuthInfo(username, null, password, null, null, domain, null)
        val params = core.createAccountParams()
        val identity = Factory.instance().createAddress("sip:$username@$domain")
        params.identityAddress = identity

        val address = Factory.instance().createAddress("sip:$domain")

        address?.transport = transportType
        params.serverAddress = address
        params.registerEnabled = true
        val account = core.createAccount(params)

        core.addAuthInfo(authInfo)
        core.addAccount(account)

        core.defaultAccount = account
        core.addListener(coreListener)
        core.start()

        // We will need the RECORD_AUDIO permission for video call
        if (packageManager.checkPermission(Manifest.permission.RECORD_AUDIO, packageName) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
            return
        }
    }

    private fun toggleVideo() {
        android.util.Log.i("Sequence", "fun toggleVideo()")


        if (core.callsNb == 0) return
        val call = if (core.currentCall != null) core.currentCall else core.calls[0]
        call ?: return

        // We will need the CAMERA permission for video call
        if (packageManager.checkPermission(Manifest.permission.CAMERA, packageName) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 0)
            return
        }

        // To update the call, we need to create a new call params, from the call object this time
        val params = core.createCallParams(call)
        // Here we toggle the video state (disable it if enabled, enable it if disabled)
        // Note that we are using currentParams and not params or remoteParams
        // params is the object you configured when the call was started
        // remote params is the same but for the remote
        // current params is the real params of the call, resulting of the mix of local & remote params
        params?.enableVideo(!call.currentParams.videoEnabled())


        // Finally we request the call update
        call.update(params)

        findViewById<TextureView>(R.id.remote_video_surface).isVisible = true
        findViewById<TextureView>(R.id.local_preview_video_surface).isVisible = false
        // Note that when toggling off the video, TextureViews will keep showing the latest frame displayed

    }

    private fun hangUp() {
        if (core.callsNb == 0) return

        // If the call state isn't paused, we can get it using core.currentCall
        val call = if (core.currentCall != null) core.currentCall else core.calls[0]
        call ?: return
        findViewById<TextureView>(R.id.remote_video_surface).isVisible = false
        // Terminating a call is quite simple
        call.terminate()
    }
}