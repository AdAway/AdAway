#ifndef ADAWAY_REDIRECTS_H
#define ADAWAY_REDIRECTS_H

#ifdef __cplusplus
extern "C" {
#endif

#include "mongoose/mongoose.h"

bool redirect(struct mg_connection *c, struct mg_http_message* hm, const char* param_name);
bool redirects(struct mg_connection *c, struct mg_http_message* hm);
void init_redirects(void);
void cleanup_redirects(void);

#ifdef __cplusplus
}
#endif
#endif //ADAWAY_REDIRECTS6_H
