#ifndef PLAUNCHER_APP_LIST_H
#define PLAUNCHER_APP_LIST_H

#define APP_LIST_MAX_APPS 20
#define APP_NAME_LEN 32
#define APP_PACKAGE_LEN 64

typedef struct {
    char name[APP_NAME_LEN];
    char package[APP_PACKAGE_LEN];
} LaunchApp;

void app_list_init(void);
int app_list_get_count(void);
int app_list_get_current_index(void);
const char* app_list_get_display_name(void);
void app_list_next(void);
void app_list_prev(void);
void app_list_set_current_index(int index);
void app_list_reset(void);
void app_list_add(const char* name, const char* package);
void app_list_clear(void);

#endif
