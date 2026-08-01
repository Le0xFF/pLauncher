#ifndef PLAUNCHER_APP_LIST_H
#define PLAUNCHER_APP_LIST_H

#include <pebble.h>

#define APP_LIST_MAX_APPS 20
#define APP_NAME_LEN 32
#define APP_PACKAGE_LEN 64
#define APP_ICON_COLOR_SIZE 1024  // 32x32, 1 byte/pixel
#define APP_ICON_BW_SIZE    128   // 32x32, 1-bit, 4-byte row padding
#define APP_ICON_SIZE PBL_IF_COLOR_ELSE(APP_ICON_COLOR_SIZE, APP_ICON_BW_SIZE)
#define APP_ICON_WIDTH 32
#define APP_ICON_HEIGHT 32

typedef struct {
    char name[APP_NAME_LEN];
    char package[APP_PACKAGE_LEN];
    uint8_t icon[APP_ICON_SIZE];
    bool has_icon;
} LaunchApp;

void app_list_init(void);
int app_list_get_count(void);
int app_list_get_current_index(void);
const char* app_list_get_display_name(void);
const uint8_t* app_list_get_icon(void);
bool app_list_has_icon(void);
void app_list_next(void);
void app_list_prev(void);
void app_list_set_current_index(int index);
void app_list_reset(void);
void app_list_add(const char* name, const char* package, const uint8_t* icon_data, uint16_t icon_len);
void app_list_clear(void);

#endif
