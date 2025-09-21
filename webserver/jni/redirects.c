#include "mongoose/mongoose.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

// Default parameter names if file cannot be read
static const char *default_param_names[] = {
        "url",
        "fallback",
        "ds_dest_url",
        "wgtarget",
        "dl_target_url"
};

// Dynamic parameter names loaded from file
static char **param_names = NULL;
static int param_names_count = 0;

// Path to the parameter names file in Android default user storage
static const char *PARAM_NAMES_FILE = "/storage/emulated/0/AdAway/skip-redirects-url-params.txt";

static void free_param_names() {
    if (param_names) {
        for (int i = 0; i < param_names_count; i++) {
            free(param_names[i]);
        }
        free(param_names);
        param_names = NULL;
        param_names_count = 0;
    }
}

static bool load_param_names_from_file() {
    FILE *file = fopen(PARAM_NAMES_FILE, "r");
    if (!file) {
        return false;
    }
    
    // First pass: count lines
    char line[256];
    int line_count = 0;
    while (fgets(line, sizeof(line), file)) {
        // Skip empty lines and comments
        if (line[0] != '\n' && line[0] != '#' && line[0] != '\0') {
            line_count++;
        }
    }
    
    if (line_count == 0) {
        fclose(file);
        return false;
    }
    
    // Allocate memory for parameter names
    param_names = malloc(line_count * sizeof(char*));
    if (!param_names) {
        fclose(file);
        return false;
    }
    
    // Second pass: read parameter names
    rewind(file);
    param_names_count = 0;
    while (fgets(line, sizeof(line), file) && param_names_count < line_count) {
        // Skip empty lines and comments
        if (line[0] == '\n' || line[0] == '#' || line[0] == '\0') {
            continue;
        }
        
        // Remove newline character
        size_t len = strlen(line);
        if (len > 0 && line[len - 1] == '\n') {
            line[len - 1] = '\0';
            len--;
        }
        
        // Remove carriage return if present
        if (len > 0 && line[len - 1] == '\r') {
            line[len - 1] = '\0';
            len--;
        }
        
        if (len > 0) {
            param_names[param_names_count] = malloc(len + 1);
            if (param_names[param_names_count]) {
                strcpy(param_names[param_names_count], line);
                param_names_count++;
            }
        }
    }
    
    fclose(file);
    return param_names_count > 0;
}

static void init_param_names() {
    // Try to load from file first
    if (!load_param_names_from_file()) {
        // Fallback to default parameters
        param_names_count = sizeof(default_param_names) / sizeof(default_param_names[0]);
        param_names = malloc(param_names_count * sizeof(char*));
        if (param_names) {
            for (int i = 0; i < param_names_count; i++) {
                size_t len = strlen(default_param_names[i]);
                param_names[i] = malloc(len + 1);
                if (param_names[i]) {
                    strcpy(param_names[i], default_param_names[i]);
                }
            }
        }
    }
}

// Call this function once during webserver startup
void init_redirects() {
    if (!param_names) {
        init_param_names();
    }
}

//static void zero_content_length(struct mg_connection *c) {
//    mg_printf(c, "%s", "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
//}

static void moved_permanently(struct mg_connection *c, const char* url) {
    //LOG(LL_DEBUG, "%s", url));
    mg_printf(c, "%s\r\n%s: %s\r\n%s\r\n\r\n",
              "HTTP/1.1 301 Moved Permanently",
              "Location", url,
              "Content-Length: 0");
}

bool redirect(struct mg_connection *c, struct mg_http_message* hm, const char* param_name) {
    const int size = 256;
    char url[size];
    int result = mg_http_get_var(&hm->query, param_name, url, size);
    if (result > 0) {
        moved_permanently(c, url);
        return true;
    }
    return false;
}

bool redirects(struct mg_connection *c, struct mg_http_message* hm) {
    bool redirected = false;
    for (int i = 0; i < param_names_count; ++i) {
        const char* param_name = param_names[i];
        //MG_DEBUG((param_name));
        redirected = redirect(c, hm, param_name);
        if (redirected) break;
    }
    return redirected;
}

// Call this function when the module is being unloaded
void cleanup_redirects() {
    free_param_names();
}
