<p align="center">
  <img src="assets/logo.png" alt="PS3 Firmware Update Server Logo" width="200"/>
</p>

<h1 align="center">PS3 Firmware Update Server</h1>

<p align="center">
  <a href="https://github.com/pcamposu/ps3-hfw-update-server/releases">
    <img src="https://img.shields.io/github/v/release/pcamposu/ps3-hfw-update-server?color=green&label=Release&logo=github" alt="GitHub Release">
  </a>
  <a href="https://github.com/pcamposu/ps3-hfw-update-server/actions/workflows/release.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/pcamposu/ps3-hfw-update-server/release.yml?branch=main&label=Build&logo=github-actions" alt="Build Status">
  </a>
  <a href="https://github.com/pcamposu/ps3-hfw-update-server/blob/main/COPYING">
    <img src="https://img.shields.io/badge/License-GPLv3-blue?logo=gnu" alt="License: GPLv3">
  </a>
  <a href="https://github.com/pcamposu/ps3-hfw-update-server/packages">
    <img src="https://img.shields.io/github/v/release/pcamposu/ps3-hfw-update-server?color=2496ED&label=Docker&logo=docker" alt="Docker Image">
  </a>
  <a href="https://java.com">
    <img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white" alt="Java 17">
  </a>
  <a href="https://spring.io/projects/spring-boot">
    <img src="https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot">
  </a>
</p>

<p align="center">
  DNS + HTTP server for PS3 firmware updates, with a Kotlin desktop app for choosing compatible CFW-CEX, CFW-PEX, CFW-DPEX, or HFW releases.
</p>

## Overview

This server intercepts PS3 update requests and serves a selected `PS3UPDAT.PUP` file instead of downloading from Sony's servers. It works by:

1. **DNS Server**: Redirects PS3 update domains (`*.ps3.update.playstation.net`) to the machine
2. **HTTP Server**: Serves the spoofed `updatelist.txt` and `PS3UPDAT.PUP` file

**Important:** The DNS server runs on port 53 (the standard DNS port). The PS3 can only be configured with an IP address for DNS, not a port number, so port 53 is mandatory.

## Quick Start (Download & Run)

### Prerequisites
- Java 17 or higher

### 1. Download the Latest Release

Go to the [Releases](https://github.com/pcamposu/ps3-hfw-update-server/releases) page and download the latest `hfwserver-*.jar` file.

### 2. Download Firmware

Use the desktop app to download a compatible release from [dracinn/Firmware-Updates](https://github.com/dracinn/Firmware-Updates), or place your own firmware file at `firmware/PS3UPDAT.PUP`:

```bash
curl -L -o firmware/PS3UPDAT.PUP \
  "https://github.com/PS3-Pro/Firmware-Updates/releases/download/HFW/Hybrid_Firmware.PUP"
```

The server reports version 9.00 to force the PS3 to always perform the update, even if 4.92 is already installed.

### 3. Prepare Directory Structure

Create the following structure:

```
ps3-hfw-update-server/
├── hfwserver-{version}.jar
└── firmware/
    └── PS3UPDAT.PUP
```

### 4. Run the Server

**Linux/Mac:**
```bash
java -jar hfwserver-{version}.jar --verbose
```

**Windows:**
```powershell
java -jar hfwserver-{version}.jar --verbose
```

The server will auto-detect your network IP address. You can also specify it manually:
```bash
java -jar hfwserver-{version}.jar --local-ip 192.168.1.100 --verbose
```

**Note:** DNS runs on port 53 and HTTP on port 80 (standard ports required by PS3).

### 5. Configure PS3

From the PlayStation 3 main menu:

1. Go to **Settings** > **Network Settings**
2. Select **Internet Connection Settings**
3. Confirm **Yes** when prompted about disconnecting from the internet
4. When prompted to "Select a setting method," choose **Custom**
5. Choose your connection method:
   - **Wired Connection** (for Ethernet cable)
   - **Wireless** (scan for your Wi-Fi network and enter the password)
6. **IP Address Setting**: Choose **Automatic** (or Manual if you prefer a static IP)
7. **DHCP Host Name**: Select **Do Not Set**
8. **DNS Setting**: Select **Manual**
9. Enter your server's IP address in both **Primary DNS** and **Secondary DNS** fields
10. Scroll through remaining settings with defaults:
    - **MTU**: Automatic
    - **Proxy Server**: Do Not Use
    - **UPnP**: Enable
11. Press **X** or **Enter** to save your settings

### 6. Run System Update

1. Make sure the server is running
2. On PS3, go to **Settings** > **System Update**
3. Select **Update via Internet**

---

## Docker Deployment (Recommended for Servers)

Docker is the easiest way to run the server 24/7 on cloud servers (tested on Linux/Mac).

### Pull Pre-built Image from GitHub Container Registry

```bash
docker pull ghcr.io/pcamposu/ps3-hfw-update-server:latest
```

### Quick Start with Docker Compose

**1. Clone repository:**
```bash
git clone https://github.com/pcamposu/ps3-hfw-update-server.git
cd ps3-hfw-update-server
```

**2. Configure LOCAL_IP:**

Edit `docker-compose.yml` and set the `LOCAL_IP` environment variable to your server's IP address:
- **LAN deployment**: Use your local network IP (e.g., `192.168.1.100`)
- **VPS/Cloud deployment**: Use your server's public IP address

**IMPORTANT**: `LOCAL_IP` is REQUIRED in Docker. The default value `192.168.1.100` MUST be changed to your actual IP address. The PS3 will be redirected to this IP, so it must be reachable from your PS3.

**3. Start server:**

Production (ports 53/80):
```bash
docker compose build --no-cache
docker compose up -d --force-recreate
```

Development (ports 53/80):
```bash
docker compose -f docker-compose.dev.yml build --no-cache
docker compose -f docker-compose.dev.yml up --force-recreate
```

**4. View logs:**
```bash
docker compose logs -f
```

**5. Stop server:**
```bash
docker compose down
```

### Cloud Deployment

Deploy to any cloud provider (AWS, GCP, Azure, DigitalOcean):

```bash
ssh user@your-server
curl -fsSL https://get.docker.com | sh
usermod -aG docker $USER

mkdir -p ~/ps3-hfw-server/firmware
cd ~/ps3-hfw-server

curl -L -o firmware/PS3UPDAT.PUP \
  "https://github.com/PS3-Pro/Firmware-Updates/releases/download/HFW/Hybrid_Firmware.PUP"

# Run container (replace YOUR_SERVER_IP with your LAN IP or public IP for VPS)
docker run -d \
  --name ps3-hfw-server \
  --restart unless-stopped \
  -p 53:53/tcp \
  -p 53:53/udp \
  -p 80:80/tcp \
  -e LOCAL_IP=YOUR_SERVER_IP \
  -e UPSTREAM_DNS=8.8.8.8 \
  -e VERBOSE=false \
  --pull always \
  --cap-add=NET_BIND_SERVICE \
  ghcr.io/pcamposu/ps3-hfw-update-server:latest
```

**Important**: Replace `YOUR_SERVER_IP` with:
- Your **LAN IP** (e.g., `192.168.1.100`) for local network deployment
- Your **public IP** for VPS/cloud deployment (use `curl -4 ifconfig.me` to find it)

**View logs:**
```bash
docker logs -f ps3-hfw-server
```

**Stop server:**
```bash
docker stop ps3-hfw-server && docker rm ps3-hfw-server
```


---

## Manual Build (Development)

### Requirements

- Java 17 or higher
- Gradle (included via wrapper)

### Kotlin Desktop GUI

The project also includes a Kotlin Swing desktop launcher for starting and stopping the existing DNS + HTTP server.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew runGui
```

The GUI lets you:

- choose/copy `PS3UPDAT.PUP` into `firmware/PS3UPDAT.PUP`
- filter remote firmware by system profile and hardware status
- choose Remote GitHub firmware or Local PS3UPDAT.PUP at the top of the window
- automatically check [dracinn/Firmware-Updates](https://github.com/dracinn/Firmware-Updates) for release `.PUP` assets in Remote mode
- choose a main firmware track: CFW-CEX, CFW-PEX, CFW-DPEX, or HFW
- choose a child variant such as Standard, noBD, noBT, or noBD+noBT when available
- download the selected firmware release into `firmware/PS3UPDAT.PUP`
- select the local IP address to use for PS3 DNS
- configure the upstream DNS server
- start and stop the server
- copy the DNS address to use on the PS3

Build the macOS app image:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew packageMacOS
```

The app is created at `build/jpackage/PS3 Firmware Update Server.app`.

DNS still binds to port 53 and HTTP still binds to port 80, so administrator privileges may be required on macOS/Linux.

### Build from Source

```bash
git clone https://github.com/pcamposu/ps3-hfw-update-server.git
cd ps3-hfw-update-server
./gradlew clean build
```

The JAR will be created at: `build/libs/hfwserver-{version}.jar`

### Run from Build

```bash
java -jar build/libs/hfwserver-{version}.jar --verbose
```

### Development Options

| Option | Description | Default |
|--------|-------------|---------|
| `--upstream-dns` | Upstream DNS server | 8.8.8.8 |
| `--local-ip` | Local IP (auto=detect) | auto |
| `-v, --verbose` | Enable verbose logging | false |
| `-h, --help` | Show help | - |

**Note:** DNS runs on port 53 and HTTP on port 80 (standard ports required by PS3).

For CLI usage, the firmware file must be at `./firmware/PS3UPDAT.PUP`.
The server reports version 9.00 to force PS3 updates (hardcoded in UpdateListService).

---

## How It Works

### DNS Redirection

When your PS3 tries to update:
1. PS3 sends DNS query for `dus01.ps3.update.playstation.net`
2. This server intercepts the query and returns the server's IP
3. PS3 connects to this server instead of Sony's servers

### HTTP Server

The HTTP server serves:
- `/update/ps3/list/{region}/ps3-updatelist.txt` - Spoofed update list with Target ID
- `/PS3UPDAT.PUP` - Your selected firmware file


---

## Development

### Project Structure

```
src/main/groovy/com/pcamposu/ps3/hfwserver/
├── Ps3HfwUpdateServerApplication.groovy
├── config/
│   ├── CliConfig.groovy
│   └── DnsServerConfig.groovy
├── dns/
│   └── Ps3DnsServer.groovy
├── http/
│   ├── controller/
│   │   └── Ps3UpdateController.groovy
│   └── service/
│       └── UpdateListService.groovy
├── model/
│   └── RegionInfo.groovy
└── util/
    └── NetworkUtils.groovy
```

### Running Tests

```bash
./gradlew test
```

---

## Credits

This project was inspired by and built upon the work of many talented developers in the PS3 homebrew scene:

@LuanTeles, @PS3Xploit, @habib, @Evilnat, @DeViL303, @zecoxao

And many others who have contributed to the PS3 scene over the years. Thank you for paving the way.

## Links

- [PS3-Pro/Firmware-Updates](https://github.com/PS3-Pro/Firmware-Updates/)
- [PS3 Dev Wiki](https://www.psdevwiki.com/ps3/)

## License

This project is licensed under the GNU General Public License v3.0. See the [COPYING](COPYING) file for details.
