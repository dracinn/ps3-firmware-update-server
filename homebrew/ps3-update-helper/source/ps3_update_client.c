#include "ps3_update_client.h"

#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#if defined(__PSL1GHT__) || defined(PS3_HELPER_USE_PSL1GHT_NET)
#include <cell/sysmodule.h>
int netInitialize(void);
int netDeinitialize(void);

int sysModuleLoad(int id)
{
    return cellSysmoduleLoadModule((uint16_t)id);
}

int sysModuleUnload(int id)
{
    return cellSysmoduleUnloadModule((uint16_t)id);
}
#endif

int ps3_update_net_start(void)
{
#if defined(__PSL1GHT__) || defined(PS3_HELPER_USE_PSL1GHT_NET)
    cellSysmoduleLoadModule(CELL_SYSMODULE_NET);
    return netInitialize();
#else
    return 0;
#endif
}

void ps3_update_net_stop(void)
{
#if defined(__PSL1GHT__) || defined(PS3_HELPER_USE_PSL1GHT_NET)
    netDeinitialize();
    cellSysmoduleUnloadModule(CELL_SYSMODULE_NET);
#endif
}

int ps3_update_http_get(const char *server_ip, const char *path, char *response, size_t response_size)
{
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        return -1;
    }

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(PS3_UPDATE_HTTP_PORT);
    addr.sin_addr.s_addr = inet_addr(server_ip);

    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        close(sock);
        return -2;
    }

    char request[512];
    int request_len = snprintf(
        request,
        sizeof(request),
        "GET %s HTTP/1.1\r\nHost: %s\r\nUser-Agent: nexus-ps3-updater\r\nConnection: close\r\n\r\n",
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

const char *ps3_update_http_body(const char *response)
{
    const char *body = strstr(response, "\r\n\r\n");
    return body == NULL ? response : body + 4;
}

int ps3_update_json_bool(const char *json, const char *key)
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

void ps3_update_json_string(const char *json, const char *key, char *out, size_t out_size)
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

int ps3_update_write_status_file(const char *server_ip, int reachable, int firmware_ready, const char *name, const char *message)
{
    FILE *file = fopen(PS3_UPDATE_STATUS_FILE, "w");
    if (file == NULL) {
        return -1;
    }

    fprintf(file, "server=%s\n", server_ip);
    fprintf(file, "reachable=%s\n", reachable ? "yes" : "no");
    fprintf(file, "firmwareReady=%s\n", firmware_ready ? "yes" : "no");
    fprintf(file, "name=%s\n", name == NULL ? "Unavailable" : name);
    fprintf(file, "message=%s\n", message == NULL ? "" : message);
    fclose(file);
    return 0;
}
