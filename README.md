# Voice Automation

**_Author_**: Matthew Ng (matthewn4444@gmail.com)

Home automation application for [LIFX Bulbs](http://www.lifx.com/) to have them
turn on when you get home (wifi connected to phone), and lights off (when wifi
disconnects from home). Lights will also turn on gradually when sunset occurs.

With a very simple voice recognition library (pocketsphinx) you can control your
music and lights from your phone. There is a bunch of settings to configure too.

## Controlling Lights

### Setup

For now this app only supports LIFX bulbs and it only works on the remote API.
You will need to login to your cloud login for LIFX to get the token. You can
add the token to settings and then you should be able to control your lights.

Follow here to get your token: [http://api.developer.lifx.com/docs/authentication](http://api.developer.lifx.com/docs/authentication)

### Voice Commands

**Activation word:** "lights"

You can follow that with the following

* **<zero -> one hundred> percent:** this will change the light percentage (0-100%)
* ** turn off:** turns off your lights
* ** turn on:** turns on your lights
* **dimmer:** dims the lights by a percentage; changable in settings, default is 5%
* **brighter:** brightens the lights by a percentage; changable in settings, default is 5%

## Controlling Music

Music is played locally from your phone.

### Voice Commands

**Quick commands (do not require activation):** "shuffle all my songs", "play next song", "play previous song"

**Activation word:** "music"

You can follow that with the following

* **play**: when paused, it will start the music again
* **pause**: when playing, it will start the pause the music
* **shuffle all my songs**: shuffles the music
* **shuffle on**: turns on shuffle mode
* **shuffle off**: turns off shuffle mode
* **repeat on**: turns on repeat mode
* **repeat off**: turns off repeat mode
* **(play) next song**: plays the next song in queue
* **(play) previous/last song**: plays the last song in queue
* **louder/volume up**: increases the volume
* **quieter/volume down**: decreases the volume
* **play this song again**: plays the current song again


## Locking Voice Commands

* Use **do not listen** to lock the app to only unlock to **start listening**