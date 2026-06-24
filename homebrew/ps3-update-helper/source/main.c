#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#if defined(__PSL1GHT__) || defined(PS3_HELPER_USE_PSL1GHT_NET)
#include <net/net.h>
#include <sysmodule/sysmodule.h>
#endif

#ifndef SERVER_IP
#define SERVER_IP "192.168.1.50"
#endif

#define HTTP_PORT 80
#define RESPONSE_CAPACITY 32768

static int ps3_net_init(void)
{
#if defined(__PSL1GHT__) || defined(PS3_HELPER_USE_PSL1GHT_NET)
    sysModuleLoad(SYSMODULE_NET);
    return netInitialize();
#else
    return 0;
#endif
}

static void ps3_net_shutdown(void)
{
#if defined(__PSL1GHT__) || defined(PS3_HELPER_USE_PSL1GHT_NET)
    netDeinitialize();
    sysModuleUnload(SYSMODULE_NET);
#endif
}

static int http_get(const char *server_ip, const char *path, char *response, size_t response_size)
{
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        return -1;
    }

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(HTTP_PORT);
    addr.sin_addr.s_addr = inet_addr(server_ip);

    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        close(sock);
        return -2;
    }

    char request[512];
    int request_len = snprintf(
        request,
        sizeof(request),
        "GET %s HTTP/1.1\r\nHost: %s\r\nUser-Agent: ps3-update-helper\r\nConnection: close\r\n\r\n",
        path,
        server_ip
    );
    if (request_len <= 0 || (size_t)request_len >= sizeof(request)) {
        close(sock);
        return -3;
    }

    if (send(sock, request, (size_t)request_len, 0) != request_len) {
        close(sock);
        return -4;
    }

    size_t total = 0;
    while (total + 1 < response_size) {
        ssize_t read_count = recv(sock, response + total, response_size - total - 1, 0);
        if (read_count < 0) {
            close(sock);
            return -5;
        }
        if (read_count == 0) {
            break;
        }
        total += (size_t)read_count;
    }

    response[total] = '\0';
    close(sock);
    return 0;
}

static const char *http_body(const char *response)
{
    const char *body = strstr(response, "\r\n\r\n");
    return body == NULL ? response : body + 4;
}

static int json_bool(const char *json, const char *key)
{
    char needle[96];
    snprintf(needle, sizeof(needle), "\"%s\"", key);

    const char *cursor = strstr(json, needle);
    if (cursor == NULL) {
        return 0;
    }

    cursor = strchr(cursor + strlen(needle), ':');
    if (cursor == NULL) {
        return 0;
    }
    cursor++;

    while (*cursor == ' ' || *cursor == '\t' || *cursor == '\r' || *cursor == '\n') {
        cursor++;
    }

    return strncmp(cursor, "true", 4) == 0;
}

static void json_string(const char *json, const char *key, char *out, size_t out_size)
{
    char needle[96];
    snprintf(needle, sizeof(needle), "\"%s\"", key);

    const char *cursor = strstr(json, needle);
    if (cursor == NULL) {
        snprintf(out, out_size, "Unavailable");
        return;
    }

    cursor = strchr(cursor + strlen(needle), ':');
    if (cursor == NULL) {
        snprintf(out, out_size, "Unavailable");
        return;
    }

    cursor = strchr(cursor, '"');
    if (cursor == NULL) {
        snprintf(out, out_size, "Unavailable");
        return;
    }
    cursor++;

    size_t index = 0;
    while (*cursor != '\0' && *cursor != '"' && index + 1 < out_size) {
        if (*cursor == '\\' && cursor[1] != '\0') {
            cursor++;
        }
        out[index++] = *cursor++;
    }
    out[index] = '\0';
}

int main(void)
{
    char response[RESPONSE_CAPACITY];
    char selected_name[256];
    char track[64];
    char variant[64];
    char install_url[128];

    printf("PS3 Update Helper\n");
    printf("Desktop server: http://%s\n\n", SERVER_IP);

    if (ps3_net_init() != 0) {
        printf("Network init failed.\n");
        return 1;
    }

    int status_result = http_get(SERVER_IP, "/api/status", response, sizeof(response));
    if (status_result != 0) {
        printf("Could not reach desktop app. Error %d\n", status_result);
        ps3_net_shutdown();
        return 1;
    }

    const char *status_json = http_body(response);
    printf("Desktop app reachable: yes\n");
    printf("Firmware ready: %s\n", json_bool(status_json, "firmwareReady") ? "yes" : "no");

    int manifest_result = http_get(SERVER_IP, "/api/firmware/manifest.json", response, sizeof(response));
    if (manifest_result != 0) {
        printf("Could not load compatibility manifest. Error %d\n", manifest_result);
        ps3_net_shutdown();
        return 1;
    }

    const char *manifest_json = http_body(response);
    json_string(manifest_json, "name", selected_name, sizeof(selected_name));
    json_string(manifest_json, "track", track, sizeof(track));
    json_string(manifest_json, "variant", variant, sizeof(variant));
    json_string(manifest_json, "installUrl", install_url, sizeof(install_url));

    printf("\nCompatible selection:\n");
    printf("  Name: %s\n", selected_name);
    printf("  Track: %s\n", track);
    printf("  Variant: %s\n", variant);
    printf("  Install URL: http://%s%s\n", SERVER_IP, install_url);

    if (!json_bool(manifest_json, "firmwareReady")) {
        printf("\nStop here: choose or download firmware in the desktop app first.\n");
        ps3_net_shutdown();
        return 1;
    }

    printf("\nReady. Set PS3 DNS to %s, then run System Update > Update via Internet.\n", SERVER_IP);
    ps3_net_shutdown();
    return 0;
}
