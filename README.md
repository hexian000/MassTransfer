![192](https://user-images.githubusercontent.com/34313493/40533929-1639bfda-6026-11e8-8db1-159f25b79e41.png)

# Mass Transfer

[![Build Status](https://travis-ci.org/hexian000/MassTransfer.svg?branch=master)](https://travis-ci.org/hexian000/MassTransfer)

The app do only transfer files between Android

For example: You can share files over WLAN or WLAN direct or hotspot between Android phones using **Mass Transfer**.

Goal: "reasonable fast" & "as simple as possible"

## User Instructions

1. Connect your devices in any of these ways:
    - Wi-Fi Direct(recommended): Typically "Network & Internet/Wi-Fi/Wi-Fi Preferences/Advanced/Wi-Fi Direct" section in your "Settings" app. It may vary between devices, some devices may not support it at all
    - Tethering(recommended): Typically "Network & Internet/Hotspot & tethering/Set-up Wi-Fi hotspot" section in your "Settings" app
    - Connect devices to same Wi-Fi. This may be slower since we have to use wireless router to forward data
    - Any other way makes devices can receive IP broadcast from each other
2. On receiver device:
    1. Launch Mass Transfer
    2. Tap "Receive" and choose a save position
3. On sender device:
    1. Launch Mass Transfer
    2. Tap IP address which you want to send to
    3. Choose the folder containing files you want to send
    4. Check files/folders to send
4. Wait until success message appears on sender device
5. Now you can disconnect your devices. Receiver may take some more seconds to finish when you are sending many small files. 

## Screenshots

![screenshot0](screenshots/screenshot0.png)

![screenshot1](screenshots/screenshot1.png)

![screenshot2](screenshots/screenshot2.png)

![screenshot3](screenshots/screenshot3.png)
