#include "ps3_update_client.h"

#include <stdio.h>
#include <string.h>

static void print_previous_status_file(void)
{
    FILE *file = fopen(PS3_UPDATE_STATUS_FILE, "r");
    if (file == NULL) {
        printf("Previous status file: not found\n\n");
        return;
    }

    printf("Previous status file:\n");

    char line[256];
    while (fgets(line, sizeof(line), file) != NULL) {
        printf("  %s", line);
        if (strchr(line, '\n') == NULL) {
            printf("\n");
        }
    }

    fclose(file);
    printf("\n");
}

int main(void)
{
    char response[PS3_UPDATE_RESPONSE_CAPACITY];
    char selected_name[256];

    printf("Nexus PS3 Updater Status\n");
    printf("Desktop server: http://%s\n\n", SERVER_IP);

    print_previous_status_file();

    if (ps3_update_net_start() != 0) {
        printf("Network init failed.\n");
        return 1;
    }

    int status_result = ps3_update_http_get(SERVER_IP, "/api/status", response, sizeof(response));
    if (status_result != 0) {
        printf("Desktop app reachable: no (error %d)\n", status_result);
        ps3_update_write_status_file(SERVER_IP, 0, 0, "Unavailable", "Status app could not reach desktop server.");
        ps3_update_net_stop();
        return 1;
    }

    const char *status_json = ps3_update_http_body(response);
    printf("Desktop app reachable: yes\n");
    printf("Firmware ready: %s\n", ps3_update_json_bool(status_json, "firmwareReady") ? "yes" : "no");

    int manifest_result = ps3_update_http_get(SERVER_IP, "/api/firmware/manifest.json", response, sizeof(response));
    if (manifest_result != 0) {
        printf("Compatibility manifest: unavailable (error %d)\n", manifest_result);
        ps3_update_net_stop();
        return 1;
    }

    const char *manifest_json = ps3_update_http_body(response);
    ps3_update_json_string(manifest_json, "name", selected_name, sizeof(selected_name));

    printf("Compatible selection: %s\n", selected_name);
    printf("\nStatus saved. Use System Update > Update via Internet to install.\n");

    ps3_update_net_stop();
    return 0;
}
