#include "strings.h"
#include <pebble.h>

static char s_empty_msg_buf[64];

const char* str_empty_message(void) {
    snprintf(s_empty_msg_buf, sizeof(s_empty_msg_buf), "No apps\nAdd via\nCompanion");
    return s_empty_msg_buf;
}
