package com.pcamposu.ps3.hfwserver.config

import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(name = "ps3-firmware-update-server", mixinStandardHelpOptions = true,
        description = "PS3 Firmware Update Server - DNS + HTTP server for PS3 firmware distribution")
class CliConfig {

    @Option(names = ["--upstream-dns"], description = "Upstream DNS server (default: \${DEFAULT-VALUE})")
    String upstreamDns = "8.8.8.8"

    @Option(names = ["--local-ip"], description = "Local IP address (default: auto-detect)")
    String localIp = "auto"

    @Option(names = ["-v", "--verbose"], description = "Enable verbose logging")
    boolean verbose = false

    @Option(names = ["-h", "--help"], usageHelp = true, description = "Show help")
    boolean helpRequested = false

    void validate() {
        if (!upstreamDns.matches(/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/)) {
            throw new IllegalArgumentException("Invalid DNS server address: " + upstreamDns)
        }

        if (localIp != "auto") {
            if (!localIp.matches(/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/)) {
                throw new IllegalArgumentException("Invalid IP address: " + localIp)
            }
            String[] parts = localIp.split("\\.")
            for (String part : parts) {
                int octet = Integer.parseInt(part)
                if (octet < 0 || octet > 255) {
                    throw new IllegalArgumentException("Invalid IP address: " + localIp)
                }
            }
        }
    }

    @Override
    String toString() {
        return """CliConfig[
            upstreamDns='$upstreamDns',
            localIp='$localIp',
            verbose=$verbose
        ]"""
    }
}
