#include "window_main.h"
#include "app_list.h"
#include "window_main_click.h"
#include <string.h>

static Window* s_window;
static TextLayer* s_text_layer;
static TextLayer* s_index_layer;
static char s_display_buf[APP_NAME_LEN];
static char s_index_buf[16];

static void window_load(Window* window) {
    Layer* window_layer = window_get_root_layer(window);
    GRect bounds = layer_get_bounds(window_layer);

    s_text_layer = text_layer_create(GRect(0, bounds.size.h / 2 - 12, bounds.size.w, 24));
    text_layer_set_text(s_text_layer, "");
    text_layer_set_text_alignment(s_text_layer, GTextAlignmentCenter);
    text_layer_set_font(s_text_layer, fonts_get_system_font(FONT_KEY_GOTHIC_24));
    layer_add_child(window_layer, text_layer_get_layer(s_text_layer));

    s_index_layer = text_layer_create(GRect(0, bounds.size.h - 20, bounds.size.w, 16));
    text_layer_set_text(s_index_layer, "");
    text_layer_set_text_alignment(s_index_layer, GTextAlignmentCenter);
    text_layer_set_font(s_index_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14));
    layer_add_child(window_layer, text_layer_get_layer(s_index_layer));

    window_set_click_config_provider(window, window_main_click_config_provider);
}

static void window_unload(Window* window) {
    text_layer_destroy(s_text_layer);
    text_layer_destroy(s_index_layer);
}

Window* window_main_create(void) {
    s_window = window_create();
    window_set_window_handlers(s_window, (WindowHandlers) {
        .load = window_load,
        .unload = window_unload,
    });
    window_main_update_display();
    return s_window;
}

void window_main_update_display(void) {
    const char* name = app_list_get_display_name();

    if (s_text_layer) {
        strncpy(s_display_buf, name, APP_NAME_LEN - 1);
        s_display_buf[APP_NAME_LEN - 1] = '\0';
        text_layer_set_text(s_text_layer, s_display_buf);
    }

    if (s_index_layer) {
        int count = app_list_get_count();
        if (count > 0) {
            snprintf(s_index_buf, sizeof(s_index_buf), "%d/%d", app_list_get_current_index() + 1, count);
            text_layer_set_text(s_index_layer, s_index_buf);
        } else {
            text_layer_set_text(s_index_layer, "");
        }
    }
}
