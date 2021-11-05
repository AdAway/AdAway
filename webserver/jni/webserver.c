#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <android/log.h>
#include <errno.h>
#include "mongoose/mongoose.h"

#define THIS_FILE "WebServer"

// TODO Test difference between 127.0.0.1 + [::1] and localhost
#define HTTP_URL "http://localhost:80"
#define HTTPS_URL "https://localhost:443"

#define OOM_ADJ_PATH    "/proc/self/oom_score_adj"
#define OOM_ADJ_NOKILL  -17

static int s_sig_num = 0;

struct settings {
    bool init;
    char ssl_cert[100];
    char ssl_key[100];
    char test_path[100];
    char icon_path[100];
    bool icon;
    bool debug;
};

static void fn(struct mg_connection *c, int ev, void *ev_data, void *fn_data) {
    if (ev == MG_EV_HTTP_MSG) {
        struct mg_http_message *hm = (struct mg_http_message *) ev_data;
        struct settings *s = (struct settings *) fn_data;
        if (mg_vcmp(&hm->uri, "/internal-test") == 0) {
            struct mg_http_serve_opts opts;
            memset(&opts, 0, sizeof(opts));
            mg_http_serve_file(c, hm, s->test_path, &opts);
        } else if (s->icon) {
            struct mg_http_serve_opts opts;
            memset(&opts, 0, sizeof(opts));
            mg_http_serve_file(c, hm, s->icon_path, &opts);
        } else {
            mg_printf(c, "%s", "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        }
    }
}

static void tls_fn(struct mg_connection *c, int ev, void *ev_data, void *fn_data) {
    if (ev == MG_EV_ACCEPT) {
        struct settings *s = (struct settings *) fn_data;
        struct mg_tls_opts tls_opts = {
                .cert = s->ssl_cert,
                .certkey= s->ssl_key
        };
        mg_tls_init(c, &tls_opts);
    } else {
        fn(c, ev, ev_data, fn_data);
    }
}

static void signal_handler(int sig_num) {
    signal(sig_num, signal_handler);
    s_sig_num = sig_num;
}

/*
 * Tell the kernel's out-of-memory killer to avoid this process.
 */
void oom_adjust_setup(void) {
    FILE *fp;
    int oom_score = INT_MIN;
    if ((fp = fopen(OOM_ADJ_PATH, "r+")) != NULL) {
        if (fscanf(fp, "%d", &oom_score) != 1)
            __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "error reading %s: %s", OOM_ADJ_PATH,
                                strerror(errno));
        else {
            rewind(fp);
            if (fprintf(fp, "%d\n", OOM_ADJ_NOKILL) <= 0)
                __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "error writing %s: %s",
                                    OOM_ADJ_PATH, strerror(errno));
            else
                __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Set %s from %d to %d",
                                    OOM_ADJ_PATH, oom_score, OOM_ADJ_NOKILL);
        }
        fclose(fp);
    }
}

struct settings parse_cli_parameters(int argc, char *argv[]) {
    struct settings s;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--resources") == 0 && i < argc - 1) {
            char *resource_path = argv[++i];
            strcpy(s.ssl_cert, resource_path);
            strcat(s.ssl_cert, "/localhost-2108.crt");
            strcpy(s.ssl_key, resource_path);
            strcat(s.ssl_key, "/localhost-2108.key");
            strcpy(s.icon_path, resource_path);
            strcat(s.icon_path, "/icon.svg");
            strcpy(s.test_path, resource_path);
            strcat(s.test_path, "/test.html");
            s.init = true;
        } else if (strcmp(argv[i], "--icon") == 0) {
            s.icon = true;
        } else if (strcmp(argv[i], "--debug") == 0) {
            s.debug = true;
        }
    }
    return s;
}

int main(int argc, char *argv[]) {
    struct mg_mgr mgr;
    struct mg_connection *http_connection;
    struct mg_connection *https_connection;

    struct settings s = parse_cli_parameters(argc, argv);
    if (!s.init) {
        __android_log_print(ANDROID_LOG_FATAL, THIS_FILE, "Missing parameters.");
        return EXIT_FAILURE;
    }

    if (s.debug) {
        __android_log_print(ANDROID_LOG_FATAL, THIS_FILE, "Debug mode activated.");
        mg_log_set("3");
    }

    oom_adjust_setup();

    mg_mgr_init(&mgr);
    http_connection = mg_http_listen(&mgr, HTTP_URL, fn, &s);
    if (http_connection == NULL) {
        __android_log_print(ANDROID_LOG_FATAL, THIS_FILE, "Failed to listen on http port.");
        return EXIT_FAILURE;
    }
    https_connection = mg_http_listen(&mgr, HTTPS_URL, tls_fn, &s);
    if (https_connection == NULL) {
        __android_log_print(ANDROID_LOG_FATAL, THIS_FILE, "Failed to listen on https port.");
        return EXIT_FAILURE;
    }

    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Starting server.");
    while (s_sig_num == 0) {
        mg_mgr_poll(&mgr, 1000);
    }

    mg_mgr_free(&mgr);
    __android_log_print(ANDROID_LOG_INFO, THIS_FILE, "Stopping server on signal %d.", s_sig_num);
    return EXIT_SUCCESS;
}
