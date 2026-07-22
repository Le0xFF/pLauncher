#include "app_list.h"
#include <string.h>

static LaunchApp s_apps[APP_LIST_MAX_APPS];
static int s_count = 0;
static int s_current_index = 0;

void app_list_init(void) {
    s_count = 0;
    s_current_index = 0;
    memset(s_apps, 0, sizeof(s_apps));
}

int app_list_get_count(void) {
    return s_count;
}

int app_list_get_current_index(void) {
    return s_current_index;
}

const char* app_list_get_display_name(void) {
    if (s_count == 0) {
        return "";
    }
    return s_apps[s_current_index].name;
}

void app_list_next(void) {
    if (s_count == 0) return;
    s_current_index++;
    if (s_current_index >= s_count) {
        s_current_index = 0;
    }
}

void app_list_prev(void) {
    if (s_count == 0) return;
    s_current_index--;
    if (s_current_index < 0) {
        s_current_index = s_count - 1;
    }
}

void app_list_set_current_index(int index) {
    if (s_count == 0) {
        s_current_index = 0;
        return;
    }
    if (index < 0) index = s_count - 1;
    if (index >= s_count) index = 0;
    s_current_index = index;
}

void app_list_reset(void) {
    s_current_index = 0;
}

void app_list_add(const char* name, const char* package) {
    if (s_count >= APP_LIST_MAX_APPS) return;
    if (name) {
        strncpy(s_apps[s_count].name, name, APP_NAME_LEN - 1);
        s_apps[s_count].name[APP_NAME_LEN - 1] = '\0';
    }
    if (package) {
        strncpy(s_apps[s_count].package, package, APP_PACKAGE_LEN - 1);
        s_apps[s_count].package[APP_PACKAGE_LEN - 1] = '\0';
    }
    s_count++;
}

void app_list_clear(void) {
    s_count = 0;
    s_current_index = 0;
    memset(s_apps, 0, sizeof(s_apps));
}
