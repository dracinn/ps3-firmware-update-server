#include "ps3_update_client.h"

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/thread.h>
#include <sys/timer.h>

#define PLUGIN_POLL_INTERVAL_SECONDS 60

static volatile int plugin_running = 1;
static sys_ppu_thread_t plugin_thread_id;

static int poll_server(void)
{
    char response[PS3_UPDATE_RESPONSE_CAPACITY];
    char selected_name[256];

    int status_result = ps3_update_http_get(SERVER_IP, "/api/status", response, sizeof(response));
    if (status_result != 0) {
        ps3_update_write_status_file(SERVER_IP, 0, 0, "Unavailable", "Desktop server was not reachable.");
        return status_result;
    }

    const char *status_json = ps3_update_http_body(response);
    int firmware_ready = ps3_update_json_bool(status_json, "firmwareReady");

    int manifest_result = ps3_update_http_get(SERVER_IP, "/api/firmware/manifest.json", response, sizeof(response));
    if (manifest_result != 0) {
        ps3_update_write_status_file(SERVER_IP, 1, firmware_ready, "Unavailable", "Desktop server is reachable, but manifest is unavailable.");
        return manifest_result;
    }

    const char *manifest_json = ps3_update_http_body(response);
    ps3_update_json_string(manifest_json, "name", selected_name, sizeof(selected_name));
    ps3_update_write_status_file(SERVER_IP, 1, firmware_ready, selected_name, "Background plugin check completed.");
    return 0;
}

static void plugin_thread(void *arg)
{
    (void)arg;

    if (ps3_update_net_start() != 0) {
        ps3_update_write_status_file(SERVER_IP, 0, 0, "Unavailable", "Plugin could not initialize networking.");
        sysThreadExit(1);
    }

    while (plugin_running) {
        poll_server();
        sys_timer_sleep(PLUGIN_POLL_INTERVAL_SECONDS);
    }

    ps3_update_net_stop();
    sysThreadExit(0);
}

int module_start(size_t argc, const void *argv)
{
    (void)argc;
    (void)argv;

    plugin_running = 1;
    return sysThreadCreate(
        &plugin_thread_id,
        plugin_thread,
        NULL,
        1500,
        0x4000,
        0,
        "nexus_update_plugin"
    );
}

int module_stop(size_t argc, const void *argv)
{
    (void)argc;
    (void)argv;

    plugin_running = 0;
    return 0;
}
