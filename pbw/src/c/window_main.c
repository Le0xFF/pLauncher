#include "window_main.h"
#include "app_list.h"
#include "window_main_click.h"
#include "packets.h"
#include "strings.h"
#include "layout.h"
#include <string.h>

static Window* s_window;
static TextLayer* s_text_layer;
static TextLayer* s_index_layer;
static TextLayer* s_label_up;
static TextLayer* s_label_down;
static TextLayer* s_label_launch;
static BitmapLayer* s_icon_layer;
static GBitmap* s_icon_bitmap;
static GRect s_screen_bounds;
static char s_display_buf[APP_NAME_LEN];
static char s_index_buf[32];

static void window_load(Window* window) {
    Layer* window_layer = window_get_root_layer(window);
    GRect bounds = layer_get_bounds(window_layer);
    s_screen_bounds = bounds;

    s_icon_bitmap = gbitmap_create_blank(
        GSize(APP_ICON_WIDTH, APP_ICON_HEIGHT),
        PBL_IF_COLOR_ELSE(GBitmapFormat8Bit, GBitmapFormat1Bit)
    );
    s_icon_layer = bitmap_layer_create(GRect(0, 0, APP_ICON_WIDTH, APP_ICON_HEIGHT));
    bitmap_layer_set_bitmap(s_icon_layer, s_icon_bitmap);
    bitmap_layer_set_compositing_mode(s_icon_layer, GCompOpSet);
    bitmap_layer_set_background_color(s_icon_layer, GColorClear);
    layer_add_child(window_layer, bitmap_layer_get_layer(s_icon_layer));

    int w = bounds.size.w;
    int h = bounds.size.h;
    int w_label = w / LAYOUT_LABEL_COL_DIVISOR;
    int w_name = w - w_label;
    int y_name = h / 2 - LAYOUT_NAME_V_OFFSET;

    s_text_layer = text_layer_create(GRect(0, y_name, w_name, LAYOUT_NAME_FONT_HEIGHT));
    text_layer_set_text(s_text_layer, "");
    text_layer_set_text_alignment(s_text_layer, GTextAlignmentCenter);
    text_layer_set_font(s_text_layer, fonts_get_system_font(FONT_KEY_GOTHIC_24));
    layer_add_child(window_layer, text_layer_get_layer(s_text_layer));

    s_index_layer = text_layer_create(GRect(0, bounds.size.h - LAYOUT_INDEX_BOTTOM_MARGIN, bounds.size.w, LAYOUT_INDEX_FONT_HEIGHT));
    text_layer_set_text(s_index_layer, "");
    text_layer_set_text_alignment(s_index_layer, GTextAlignmentCenter);
    text_layer_set_font(s_index_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14));
    layer_add_child(window_layer, text_layer_get_layer(s_index_layer));

    int h_label = LAYOUT_LABEL_FONT_HEIGHT;
    int y_step = h / LAYOUT_LABEL_ZONES;
    int y_up = y_step / 2 - h_label / 2;
    int y_launch = y_step + y_up;
    int y_down = 2 * y_step + y_up;

    s_label_up = text_layer_create(GRect(w - w_label, y_up, w_label, h_label));
    text_layer_set_text(s_label_up, STR_LABEL_UP);
    text_layer_set_text_alignment(s_label_up, GTextAlignmentCenter);
    text_layer_set_font(s_label_up, fonts_get_system_font(FONT_KEY_GOTHIC_18));
    layer_add_child(window_layer, text_layer_get_layer(s_label_up));

    s_label_launch = text_layer_create(GRect(w - w_label, y_launch, w_label, h_label));
    text_layer_set_text(s_label_launch, STR_LABEL_LAUNCH);
    text_layer_set_text_alignment(s_label_launch, GTextAlignmentCenter);
    text_layer_set_font(s_label_launch, fonts_get_system_font(FONT_KEY_GOTHIC_18));
    layer_add_child(window_layer, text_layer_get_layer(s_label_launch));

    s_label_down = text_layer_create(GRect(w - w_label, y_down, w_label, h_label));
    text_layer_set_text(s_label_down, STR_LABEL_DOWN);
    text_layer_set_text_alignment(s_label_down, GTextAlignmentCenter);
    text_layer_set_font(s_label_down, fonts_get_system_font(FONT_KEY_GOTHIC_18));
    layer_add_child(window_layer, text_layer_get_layer(s_label_down));

    window_set_click_config_provider(window, window_main_click_config_provider);
}

static void window_unload(Window* window) {
    text_layer_destroy(s_label_up);
    text_layer_destroy(s_label_down);
    text_layer_destroy(s_label_launch);
    text_layer_destroy(s_text_layer);
    text_layer_destroy(s_index_layer);
    bitmap_layer_destroy(s_icon_layer);
    gbitmap_destroy(s_icon_bitmap);
}

static void window_appear(Window* window) {
    request_app_list();
    window_main_update_display();
}

Window* window_main_create(void) {
    s_window = window_create();
    window_set_window_handlers(s_window, (WindowHandlers) {
        .load = window_load,
        .appear = window_appear,
        .unload = window_unload,
    });
    window_main_update_display();
    return s_window;
}

Window* window_main_get_window(void) {
    return s_window;
}

void window_main_update_display(void) {
    if (!s_text_layer) return;

    int count = app_list_get_count();

    if (count > 0) {
        const char* name = app_list_get_display_name();
        strncpy(s_display_buf, name, APP_NAME_LEN - 1);
        s_display_buf[APP_NAME_LEN - 1] = '\0';
        text_layer_set_text(s_text_layer, s_display_buf);

        int w = s_screen_bounds.size.w;
        int h = s_screen_bounds.size.h;
        int w_label = w / LAYOUT_LABEL_COL_DIVISOR;
        int w_name = w - w_label;

        int icon_x = (w_name - LAYOUT_ICON_SIZE) / 2;
        int icon_y = (h - LAYOUT_NAME_FONT_HEIGHT - LAYOUT_ICON_SIZE - LAYOUT_ICON_V_PADDING * 2) / 2;
        layer_set_frame(bitmap_layer_get_layer(s_icon_layer), GRect(icon_x, icon_y, LAYOUT_ICON_SIZE, LAYOUT_ICON_SIZE));

        if (app_list_has_icon()) {
            memcpy(gbitmap_get_data(s_icon_bitmap), app_list_get_icon(), APP_ICON_SIZE);
            bitmap_layer_set_bitmap(s_icon_layer, s_icon_bitmap);
            layer_set_hidden((Layer *)s_icon_layer, false);
            APP_LOG(APP_LOG_LEVEL_DEBUG, "Icon displayed for: %s", s_display_buf);
        } else {
            layer_set_hidden((Layer *)s_icon_layer, true);
            APP_LOG(APP_LOG_LEVEL_DEBUG, "No icon for: %s", s_display_buf);
        }

        int text_y = icon_y + LAYOUT_ICON_SIZE + LAYOUT_ICON_V_PADDING;
        layer_set_frame(text_layer_get_layer(s_text_layer), GRect(0, text_y, w_name, LAYOUT_NAME_FONT_HEIGHT));

        snprintf(s_index_buf, sizeof(s_index_buf), "%d/%d", app_list_get_current_index() + 1, count);
        text_layer_set_text(s_index_layer, s_index_buf);
        layer_set_frame(text_layer_get_layer(s_index_layer), GRect(0, h - LAYOUT_INDEX_BOTTOM_MARGIN, w_name, LAYOUT_INDEX_FONT_HEIGHT));
        layer_set_hidden((Layer *)s_index_layer, false);

        layer_set_hidden((Layer *)s_label_up, false);
        layer_set_hidden((Layer *)s_label_down, false);
        layer_set_hidden((Layer *)s_label_launch, false);
    } else {
        text_layer_set_text(s_text_layer, packets_is_loading() ? STR_LOADING_MESSAGE : str_empty_message());
        layer_set_hidden((Layer *)s_icon_layer, true);

        int w = s_screen_bounds.size.w;
        int h = s_screen_bounds.size.h;
        int w_label = w / LAYOUT_LABEL_COL_DIVISOR;
        int w_name = w - w_label;
        layer_set_frame(text_layer_get_layer(s_text_layer), GRect(0, 0, w_name, h));
        GSize content_size = text_layer_get_content_size(s_text_layer);
        int y_empty = (h - content_size.h) / 2;
        layer_set_frame(text_layer_get_layer(s_text_layer), GRect(0, y_empty, w_name, content_size.h));

        text_layer_set_text(s_index_layer, "");
        layer_set_hidden((Layer *)s_index_layer, true);
    }
}
