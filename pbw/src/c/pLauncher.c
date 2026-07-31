#include <pebble.h>
#include "packets.h"
#include "app_list.h"
#include "window_main.h"

static void init(void) {
    packets_init();
    load_vibration_pref();
    load_auto_close_pref();
    app_list_init();
    Window* window = window_main_create();
    bool animated = true;
    window_stack_push(window, animated);
}

static void deinit(void) {
    packets_deinit();
}

int main(void) {
    init();
    APP_LOG(APP_LOG_LEVEL_DEBUG, "pLauncher initialized");
    app_event_loop();
    deinit();
}
