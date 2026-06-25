#ifndef PS3_UPDATE_CLIENT_H
#define PS3_UPDATE_CLIENT_H

#include <stddef.h>

#ifndef SERVER_IP
#define SERVER_IP "192.168.1.50"
#endif

#define PS3_UPDATE_STATUS_FILE "/dev_hdd0/tmp/nexus-update-plugin-status.txt"
#define PS3_UPDATE_HTTP_PORT 80
#define PS3_UPDATE_RESPONSE_CAPACITY 32768

int ps3_update_net_start(void);
void ps3_update_net_stop(void);
int ps3_update_http_get(const char *server_ip, const char *path, char *response, size_t response_size);
const char *ps3_update_http_body(const char *response);
int ps3_update_json_bool(const char *json, const char *key);
void ps3_update_json_string(const char *json, const char *key, char *out, size_t out_size);
int ps3_update_write_status_file(const char *server_ip, int reachable, int firmware_ready, const char *name, const char *message);

#endif
