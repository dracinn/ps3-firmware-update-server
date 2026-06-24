package com.pcamposu.ps3.hfwserver.http.controller

import com.pcamposu.ps3.hfwserver.http.service.UpdateListService
import com.pcamposu.ps3.hfwserver.model.RegionInfo
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import java.io.File

@Slf4j
@RestController
@RequestMapping
class Ps3UpdateController {

    private final UpdateListService updateListService
    private final String firmwarePath
    private final String manifestPath

    Ps3UpdateController(
            UpdateListService updateListService,
            @Value('${ps3.firmware.path:firmware/PS3UPDAT.PUP}') String firmwarePath,
            @Value('${ps3.manifest.path:}') String manifestPath
    ) {
        this.updateListService = updateListService
        this.firmwarePath = firmwarePath
        this.manifestPath = manifestPath
    }

    @GetMapping(value = ["/update/ps3/list/{region}/ps3-updatelist.txt", "/update/ps3/list/{region}/updatelist.txt"],
            produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> getUpdateList(@PathVariable('region') String region) {
        log.info("[HTTP] GET /update/ps3/list/$region/ps3-updatelist.txt [Region: ${region.toUpperCase()}]")

        String content = updateListService.generateUniversalUpdateList()

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header("Content-Length", String.valueOf(content.length()))
                .body(content)
    }

    @GetMapping("/PS3UPDAT.PUP")
    ResponseEntity<Resource> getFirmware() {
        File firmwareFile = new File(firmwarePath)

        log.info("[HTTP] GET /PS3UPDAT.PUP")

        if (!firmwareFile.exists()) {
            log.error("[HTTP] PS3UPDAT.PUP not found at: $firmwarePath")
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(null)
        }

        log.info("[HTTP] Serving PS3UPDAT.PUP [Size: ${firmwareFile.length()} bytes]")

        Resource resource = new FileSystemResource(firmwareFile)

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"PS3UPDAT.PUP\"")
                .header("Content-Length", String.valueOf(firmwareFile.length()))
                .body(resource)
    }

    @GetMapping(value = ["/update/ps3/image/{region}/**", "/update/ps3/image/{region}"])
    ResponseEntity<Resource> getFirmwareAlternate(@PathVariable('region') String region) {
        log.info("[HTTP] GET /update/ps3/image/$region/** -> redirecting to PS3UPDAT.PUP")
        return getFirmware()
    }

    @GetMapping("/")
    ResponseEntity<String> getRoot() {
        File firmwareFile = new File(firmwarePath)
        String canonicalPath = firmwareFile.canonicalPath

        String message = "PS3 Firmware Update Server\n\n" +
                "DNS Server: Running\n" +
                "HTTP Server: Running\n" +
                "Firmware Path: $firmwarePath\n\n" +
                "Place your PS3UPDAT.PUP file at: $canonicalPath"

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(message)
    }

    @GetMapping(value = "/api/status", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getStatus() {
        File firmwareFile = new File(firmwarePath)
        String body = "{" +
                "\"app\":\"PS3 Firmware Update Server\"," +
                "\"http\":\"running\"," +
                "\"dns\":\"running\"," +
                "\"firmwareReady\":${firmwareFile.exists()}," +
                "\"firmwarePath\":\"${jsonEscape(firmwareFile.absolutePath)}\"," +
                "\"firmwareSizeBytes\":${firmwareFile.exists() ? firmwareFile.length() : 0}," +
                "\"installUrl\":\"/PS3UPDAT.PUP\"," +
                "\"manifestUrl\":\"/api/firmware/manifest.json\"" +
                "}"

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
    }

    @GetMapping(value = "/api/firmware/manifest.json", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getFirmwareManifest() {
        File manifestFile = manifestPath ? new File(manifestPath) : null
        if (manifestFile != null && manifestFile.exists()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(manifestFile.text)
        }

        File firmwareFile = new File(firmwarePath)
        String body = "{" +
                "\"app\":\"PS3 Firmware Update Server\"," +
                "\"sourceMode\":\"Unknown\"," +
                "\"firmwareReady\":${firmwareFile.exists()}," +
                "\"firmwarePath\":\"${jsonEscape(firmwareFile.absolutePath)}\"," +
                "\"installUrl\":\"/PS3UPDAT.PUP\"," +
                "\"instructions\":\"Start the desktop app and select a compatible firmware before installing on the PS3.\"," +
                "\"selected\":null," +
                "\"compatibleFirmware\":[]" +
                "}"

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
    }

    private static String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
    }
}
