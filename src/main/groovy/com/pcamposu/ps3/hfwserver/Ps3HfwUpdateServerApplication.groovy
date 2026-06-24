package com.pcamposu.ps3.hfwserver

import com.pcamposu.ps3.hfwserver.config.CliConfig
import com.pcamposu.ps3.hfwserver.dns.Ps3DnsServer
import com.pcamposu.ps3.hfwserver.util.NetworkUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.*
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import picocli.CommandLine

import java.util.concurrent.atomic.AtomicReference

@Slf4j
@CompileStatic
@SpringBootApplication
class Ps3HfwUpdateServerApplication {

	static void main(String[] args) {
		CliConfig cliConfig = new CliConfig()
		CommandLine cmd = new CommandLine(cliConfig)

		try {
			def parseResult = cmd.parseArgs(args)
			if (parseResult.isUsageHelpRequested()) {
				cmd.usage(cmd.out)
				System.exit(0)
			}
			cliConfig.validate()
		} catch (Exception ex) {
			cmd.err.println("Error: ${ex.message}")
			cmd.err.println("Run with --help for usage information")
			System.exit(1)
		}

		String detectedIp = cliConfig.localIp == "auto" ? NetworkUtils.getBestLocalIp() : cliConfig.localIp
		String firmwarePath = "./firmware/PS3UPDAT.PUP"
		int dnsPort = 53
		int httpPort = 80

		System.setProperty("server.port", String.valueOf(httpPort))
		System.setProperty("firmware.path", firmwarePath)
		System.setProperty("upstream.dns", cliConfig.upstreamDns)
		System.setProperty("local.ip", detectedIp)
		System.setProperty("ps3.verbose", String.valueOf(cliConfig.verbose))
		System.setProperty("verbose", String.valueOf(cliConfig.verbose))
		System.setProperty("ps3.dns.port", String.valueOf(dnsPort))
		System.setProperty("ps3.dns.upstream", cliConfig.upstreamDns)
		System.setProperty("ps3.dns.localIp", detectedIp)
		System.setProperty("ps3.firmware.path", firmwarePath)

		SpringApplication app = new SpringApplication(Ps3HfwUpdateServerApplication)
		app.setBannerMode(Banner.Mode.OFF)
		app.setLogStartupInfo(false)
		app.setRegisterShutdownHook(true)

		def contextRef = new AtomicReference<ConfigurableApplicationContext>()
		def dnsServerRef = new AtomicReference<Ps3DnsServer>()

		app.addListeners(new ApplicationListener<ApplicationReadyEvent>() {
			@Override
			void onApplicationEvent(ApplicationReadyEvent event) {
				ConfigurableApplicationContext context = event.applicationContext
				contextRef.set(context)

				if (cliConfig.verbose) {
					ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
					rootLogger.setLevel(ch.qos.logback.classic.Level.DEBUG)

					ch.qos.logback.classic.Logger appLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("com.pcamposu.ps3.hfwserver")
					appLogger.setLevel(ch.qos.logback.classic.Level.DEBUG)
				}

				try {
					Ps3DnsServer dnsServer = context.getBean(Ps3DnsServer)
					dnsServerRef.set(dnsServer)
					dnsServer.start()

					if (cliConfig.verbose) {
						println ""
						println "============================================"
						println "  PS3 Firmware Update Server"
						println "============================================"
						println ""
						println "DNS Server:   Running on port 53"
						println "HTTP Server:  Running on port 80"
						println "Local IP:     ${detectedIp}"
						println ""
						if (cliConfig.localIp == "auto") {
							println NetworkUtils.displayAvailableIps()
							println ""
						}
						println ""
						println "Configure your PS3 DNS settings to: ${detectedIp}"
						println ""
						println "Press Ctrl+C to stop the server"
						println "============================================"
						println ""
					} else {
						log.info("PS3 Firmware Update Server started - DNS: port 53, HTTP: port 80, IP: ${detectedIp}")
						log.info("Configure your PS3 DNS settings to: ${detectedIp}")
					}
				} catch (Exception e) {
					log.error("Failed to start DNS server: ${e.message}", e)
					System.exit(1)
				}
			}
		})

		def context = app.run(args)

		Runtime.getRuntime().addShutdownHook(new Thread({
			log.info("Shutting down...")
			Ps3DnsServer dnsServer = dnsServerRef.get()
			if (dnsServer != null && dnsServer.isRunning()) {
				dnsServer.stop()
			}
			context.close()
			log.info("Shutdown complete")
		}))

		synchronized(Ps3HfwUpdateServerApplication.class) {
			try {
				Ps3HfwUpdateServerApplication.class.wait()
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt()
			}
		}
	}
}
