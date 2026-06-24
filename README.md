<p align="center">
  <img src="assets/logo.png" alt="PS3 Firmware Update Server Logo" width="200"/>
</p>

<h1 align="center">PS3 Firmware Update Server</h1>

<p align="center">
  macOS desktop app for serving compatible PS3 firmware updates over DNS + HTTP.
</p>

<p align="center">
  <a href="https://github.com/dracinn/ps3-firmware-update-server">dracinn/ps3-firmware-update-server</a>
</p>

## Overview

PS3 Firmware Update Server lets a PS3 install a selected firmware PUP through **System Update > Update via Internet**. The app runs a local DNS server on port 53 and an HTTP server on port 80, redirects PS3 update domains to your Mac, and serves the selected `PS3UPDAT.PUP`.

The Kotlin desktop app can download compatible firmware from [dracinn/Firmware-Updates](https://github.com/dracinn/Firmware-Updates) or use a local `PS3UPDAT.PUP` file.

The repository also includes a PS3 homebrew helper scaffold in `homebrew/ps3-update-helper`. The helper can query the running desktop app, confirm that compatible firmware is ready, and show the selected install target before you run the normal PS3 update flow.

## Firmware Tracks

Remote mode follows the firmware host repository layout:

| Main track | Child variants |
| --- | --- |
| CFW | Standard, noBD, noBT, noBD + noBT |
| CFW-PEX | Standard, noBD, noBT, noBD + noBT |
| DBG / D-PEX | Standard, noBD, noBT, noBD + noBT |
| HFW | HFW |

The app filters remote firmware by:

- system profile: HFW/HEN or unknown CFW compatibility, retail CEX CFW-compatible, or Debug/DEX
- hardware status: standard hardware, noBD, noBT, or noBD + noBT

## Build The macOS App

Requirements:

- Java 17 or higher
- Gradle wrapper included in this repository

Build and package the app:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew build packageMacOS
```

The app is created at:

```text
build/jpackage/PS3 Firmware Update Server.app
```

## Using The App

1. Open `PS3 Firmware Update Server.app`.
2. Select your system profile and hardware status.
3. Choose `Remote GitHub firmware` or `Local PS3UPDAT.PUP`.
4. In remote mode, choose the main and child firmware variants, then download the matching remote firmware.
5. In local mode, choose an existing trusted `PS3UPDAT.PUP` file.
6. Select the local IP address your PS3 can reach.
7. Start the server.
8. On the PS3, set both primary and secondary DNS to the app's local IP address.
9. Run **Settings > System Update > Update via Internet**.

DNS port 53 and HTTP port 80 are required by the PS3 update flow. macOS may require administrator privileges or firewall approval for those ports.

Downloaded or selected firmware is stored at:

```text
~/Library/Application Support/PS3 Firmware Update Server/firmware/PS3UPDAT.PUP
```

## How It Works

1. The PS3 asks DNS for update domains such as `dus01.ps3.update.playstation.net`.
2. The local DNS server returns your Mac's IP address for PS3 update domains.
3. The PS3 downloads the spoofed update list from the local HTTP server.
4. The PS3 downloads `PS3UPDAT.PUP` from the local HTTP server.

The app also exposes local JSON endpoints for the homebrew helper:

```text
http://<mac-ip>/api/status
http://<mac-ip>/api/firmware/manifest.json
```

The manifest contains only the firmware entries compatible with the currently selected system profile, hardware status, main variant, and child variant.

## Project Structure

```text
dracinn/ps3-firmware-update-server
├── README.md
├── build.gradle
├── settings.gradle
├── assets/
│   └── logo.png
├── firmware/
│   └── README.md
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── homebrew/ps3-update-helper/
│   ├── Makefile
│   ├── README.md
│   ├── CMakeLists.txt
│   └── source/
│       └── main.c
├── src/main/groovy/com/pcamposu/ps3/hfwserver/
│   ├── Ps3HfwUpdateServerApplication.groovy
│   ├── config/
│   │   └── DnsServerConfig.groovy
│   ├── dns/
│   │   └── Ps3DnsServer.groovy
│   ├── http/
│   │   ├── controller/
│   │   │   └── Ps3UpdateController.groovy
│   │   └── service/
│   │       └── UpdateListService.groovy
│   ├── model/
│   │   └── RegionInfo.groovy
│   └── util/
│       └── NetworkUtils.groovy
├── src/main/kotlin/com/pcamposu/ps3/hfwserver/gui/
│   └── Ps3HfwUpdateServerGui.kt
├── src/main/resources/
│   ├── application.properties
│   └── logback-spring.xml
└── src/test/groovy/com/pcamposu/ps3/hfwserver/
    ├── dns/
    │   └── Ps3DnsServerTest.groovy
    └── http/service/
        └── UpdateListServiceTest.groovy
```

## Tests

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test
```

## Credits

Built on the original PS3 update server work and the PS3 homebrew scene, including PS3Xploit, Evilnat, Joonie, Littlebalup, and many others.

## License

This project is licensed under the GNU General Public License v3.0. See [COPYING](COPYING) for details.
