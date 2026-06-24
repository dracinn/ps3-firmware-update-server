# PS3 Firmware Directory

Place your PS3UPDAT.PUP file in this directory.

## How to Download Firmware

1. Use the desktop app to download a compatible release from [dracinn/Firmware-Updates](https://github.com/dracinn/Firmware-Updates)
2. Or download a trusted CFW-CEX, CFW-PEX, CFW-DPEX, or HFW release manually
3. Extract the downloaded archive
4. Find the `PS3UPDAT.PUP` file
5. Copy it to this directory
6. Rename it to `PS3UPDAT.PUP` (if not already named)

## File Location

The server expects the file at:
```
./firmware/PS3UPDAT.PUP
```

Or from the project root:
```
ps3-hfw-update-server/firmware/PS3UPDAT.PUP
```

## Alternative Location

You can specify a custom path using the `--firmware-path` option:

```bash
java -jar build/libs/ps3-hfw-update-server-0.0.1-SNAPSHOT.jar --firmware-path /path/to/your/PS3UPDAT.PUP
```

## File Verification

A valid PS3UPDAT.PUP file should be:
- Approximately 190-200 MB in size
- Named exactly `PS3UPDAT.PUP`
- Not corrupted or incomplete

**Note:** Do NOT use official Sony firmware PUP files. Use only trusted CFW-CEX, CFW-PEX, CFW-DPEX, or HFW PUP files.
