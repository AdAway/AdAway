#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <android/log.h>
#include <errno.h>
#include "mongoose/mongoose.h"

#define THIS_FILE "AdAway"

static int s_sig_num = 0;
static char ssl_cert[100];
static char ssl_key[100];
static char test_path[100];
static char icon_path[100];
static bool icon = 0;

static void ev_handler(struct mg_connection *c, int ev, void *p) {
    if (ev == MG_EV_HTTP_REQUEST) {
        struct http_message *hm = (struct http_message *) p;
        if (mg_vcmp(&hm->uri, "/internal-test") == 0) {
            mg_http_serve_file(c, hm, test_path, mg_mk_str("text/html"), mg_mk_str(""));
        } else if (icon) {
            mg_http_serve_file(c, hm, icon_path, mg_mk_str("image/svg+xml"), mg_mk_str(""));
        } else {
            mg_send_head(c, 200, 0, "Content-Type: text/plain");
        }
    }
}

static void signal_handler(int sig_num) {
    signal(sig_num, signal_handler);
    s_sig_num = sig_num;
}

#define OOM_ADJ_PATH    "/proc/self/oom_score_adj"
#define OOM_ADJ_NOKILL  -17
static int oom_adj_save = INT_MIN;

/*
* Tell the kernel's out-of-memory killer to avoid this process.
* Returns the previous oom_score_adj value or zero.
*/
void oom_adjust_setup(void) {
    FILE *fp;
    if ((fp = fopen(OOM_ADJ_PATH, "r+")) != NULL) {
        if (fscanf(fp, "%d", &oom_adj_save) != 1)
            __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "error reading %s: %s", OOM_ADJ_PATH, strerror(errno));
        else {
            rewind(fp);
            if (fprintf(fp, "%d\n", OOM_ADJ_NOKILL) <= 0)
                __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "error writing %s: %s", OOM_ADJ_PATH, strerror(errno));
            else
                __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Set %s from %d to %d", OOM_ADJ_PATH, oom_adj_save, OOM_ADJ_NOKILL);
        }
        fclose(fp);
    }
}

bool parse_cli_parameters(int argc, char *argv[]) {
    bool init = 0;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--resources") == 0 && i < argc - 1) {
            char *resource_path = argv[++i];
            strcpy(ssl_cert, resource_path);
            strcat(ssl_cert, "/localhost.crt");
            strcpy(ssl_key, resource_path);
            strcat(ssl_key, "/localhost.key");
            strcpy(icon_path, resource_path);
            strcat(icon_path, "/icon.svg");
            strcpy(test_path, resource_path);
            strcat(test_path, "/test.html");
            init = 1;
        } else if (strcmp(argv[i], "--icon") == 0) {
            icon = 1;
        }
    }
    return init;
}

int main(int argc, char *argv[]) {
    struct mg_mgr mgr;
    struct mg_connection *nc1;
    struct mg_connection *nc2;
    struct mg_connection *nc3;
    struct mg_connection *nc4;
    struct mg_bind_opts bind_opts;

    const char *ipv4HttpAddress = "127.0.0.1:80";
    const char *ipv6HttpAddress = "[::1]:80";
    const char *ipv4HttpsAddress = "127.0.0.1:443";
    const char *ipv6HttpsAddress = "[::1]:443";

    if (!parse_cli_parameters(argc, argv)) {
        return EXIT_FAILURE;
    }

    const char *err;
    memset(&bind_opts, 0, sizeof(bind_opts));
    bind_opts.error_string = &err;
    bind_opts.ssl_cert = ssl_cert;
    bind_opts.ssl_key = ssl_key;

    oom_adjust_setup();

    mg_mgr_init(&mgr, NULL);
    nc1 = mg_bind(&mgr, ipv4HttpAddress, ev_handler);
    nc2 = mg_bind(&mgr, ipv6HttpAddress, ev_handler);
    nc3 = mg_bind_opt(&mgr, ipv4HttpsAddress, ev_handler, bind_opts);
    if (nc3 == NULL) {
        __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Failed to bind server: %s", err);
        return EXIT_FAILURE;
    }
    nc4 = mg_bind_opt(&mgr, ipv6HttpsAddress, ev_handler, bind_opts);
    if (nc4 == NULL) {
        __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Failed to bind server: %s", err);
        return EXIT_FAILURE;
    }

    mg_set_protocol_http_websocket(nc1);
    mg_set_protocol_http_websocket(nc2);
    mg_set_protocol_http_websocket(nc3);
    mg_set_protocol_http_websocket(nc4);

    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "AdAway Native Webserver: starting");
    while (s_sig_num == 0) {
        mg_mgr_poll(&mgr, 1000);
    }

    mg_mgr_free(&mgr);
    __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "AdAway Native Webserver: exited on signal %d", s_sig_num);
    return EXIT_SUCCESS;
}
