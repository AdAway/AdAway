#ifndef ADAWAY_REDIRECTS_H
#define ADAWAY_REDIRECTS_H

#ifdef __cplusplus
extern "C" {
#endif

#include "mongoose/mongoose.h"

void redirect(struct mg_connection *c, struct mg_http_message* hm, const char* param_name);
void redirects(struct mg_connection *c, struct mg_http_message* hm);

#ifdef __cplusplus
}
#endif
#endif //ADAWAY_REDIRECTS6_H
