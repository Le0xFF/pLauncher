#include "window_main_click.h"
#include "window_main.h"
#include "app_list.h"
#include "packets.h"

static void select_click_handler(ClickRecognizerRef recognizer, void* context) {
    if (packets_is_loading()) {
        return;
    }
    int index = app_list_get_current_index();
    send_launch_app(index);
}

static void up_click_handler(ClickRecognizerRef recognizer, void* context) {
    if (packets_is_loading()) {
        return;
    }
    app_list_prev();
    window_main_update_display();
}

static void down_click_handler(ClickRecognizerRef recognizer, void* context) {
    if (packets_is_loading()) {
        return;
    }
    app_list_next();
    window_main_update_display();
}

void window_main_click_config_provider(void* context) {
    window_single_repeating_click_subscribe(BUTTON_ID_UP, 200, up_click_handler);
    window_single_repeating_click_subscribe(BUTTON_ID_DOWN, 200, down_click_handler);
    window_single_click_subscribe(BUTTON_ID_SELECT, select_click_handler);
}
