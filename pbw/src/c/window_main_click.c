/*
 * pLauncher Watchapp — Button click handlers. Navigation (up/down) and app launch (select) with loading guards.
 *
 * Author: Le0xFF
 */
#include "window_main_click.h"
#include "window_main.h"
#include "app_list.h"
#include "packets.h"

// Guard: ignore select while app list is still loading.
static void select_click_handler(ClickRecognizerRef recognizer, void* context) {
    if (packets_is_loading()) {
        return;
    }
    int index = app_list_get_current_index();
    send_launch_app(index);
}

// Guard: ignore navigation while loading. Uses 200ms repeat rate for fast scrolling.
static void up_click_handler(ClickRecognizerRef recognizer, void* context) {
    if (packets_is_loading()) {
        return;
    }
    app_list_prev();
    window_main_update_display();
}

// Guard: ignore navigation while loading. Uses 200ms repeat rate for fast scrolling.
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
