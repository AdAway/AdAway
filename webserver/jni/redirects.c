#include "mongoose/mongoose.h"

static const char *param_names[] = {
        "url",
        "fallback",
        "ds_dest_url",
        "wgtarget"
};

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
    static const int size = sizeof(param_names)/sizeof(param_names[0]);
    bool redirected = false;
    for (int i = 0 ; i < size; ++i) {
        const char* param_name = param_names[i];
        //MG_DEBUG((param_name));
        redirected = redirect(c, hm, param_name);
        if (redirected) break;
    }
    return redirected;
}
