#include "app_list.h"
#include <pebble.h>
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

const uint8_t* app_list_get_icon(void) {
    if (s_count == 0) {
        return NULL;
    }
    return s_apps[s_current_index].icon;
}

bool app_list_has_icon(void) {
    if (s_count == 0) {
        return false;
    }
    return s_apps[s_current_index].has_icon;
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

void app_list_add(const char* name, const char* package, const uint8_t* icon_data, uint16_t icon_len) {
    if (s_count >= APP_LIST_MAX_APPS) return;
    if (name) {
        strncpy(s_apps[s_count].name, name, APP_NAME_LEN - 1);
        s_apps[s_count].name[APP_NAME_LEN - 1] = '\0';
    }
    if (package) {
        strncpy(s_apps[s_count].package, package, APP_PACKAGE_LEN - 1);
        s_apps[s_count].package[APP_PACKAGE_LEN - 1] = '\0';
    }
    if (icon_data != NULL && icon_len == APP_ICON_SIZE) {
        memcpy(s_apps[s_count].icon, icon_data, APP_ICON_SIZE);
        s_apps[s_count].has_icon = true;
        APP_LOG(APP_LOG_LEVEL_DEBUG, "App icon stored: %s (len=%d)", name ? name : "null", icon_len);
    } else {
        s_apps[s_count].has_icon = false;
        if (icon_data != NULL) {
            APP_LOG(APP_LOG_LEVEL_DEBUG, "App icon rejected: %s (len=%d, expected=%d)", name ? name : "null", icon_len, APP_ICON_SIZE);
        }
    }
    s_count++;
}

void app_list_clear(void) {
    s_count = 0;
    s_current_index = 0;
    memset(s_apps, 0, sizeof(s_apps));
}
