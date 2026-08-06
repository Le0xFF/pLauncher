/*
 * pLauncher Watchapp — Main window UI implementation. Creates layers, handles dynamic layout for basalt/emery screens.
 *
 * Author: Le0xFF
 */
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
static BitmapLayer* s_icon_up;
static BitmapLayer* s_icon_down;
static BitmapLayer* s_icon_launch;
static GBitmap* s_bitmap_caret_up;
static GBitmap* s_bitmap_caret_down;
static GBitmap* s_bitmap_rocket;
static BitmapLayer* s_icon_layer;
static GBitmap* s_icon_bitmap;
static GRect s_screen_bounds;
static char s_display_buf[APP_NAME_LEN];
static char s_index_buf[32];

// Calculate centered text frame with word-wrap area and dynamic height.
static void calc_text_frame(int *out_x, int *out_width) {
    int w = s_screen_bounds.size.w;
    int nav_x = w - (w / LAYOUT_NAV_COL_DIVISOR);
    *out_width = 2 * nav_x - w - LAYOUT_TEXT_H_MARGIN;
    *out_x = (w - *out_width) / 2;
}

// Create all UI layers: icon, name text, index text, navigation bitmaps.
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
    int y_name = h / 2 - LAYOUT_NAME_V_OFFSET;

    int text_x, text_width;
    calc_text_frame(&text_x, &text_width);
    int nav_x = w - (w / LAYOUT_NAV_COL_DIVISOR);
    s_text_layer = text_layer_create(GRect(text_x, y_name, text_width, LAYOUT_NAME_MAX_HEIGHT));
    text_layer_set_text(s_text_layer, "");
    text_layer_set_text_alignment(s_text_layer, GTextAlignmentCenter);
    text_layer_set_font(s_text_layer, fonts_get_system_font(FONT_KEY_GOTHIC_24));
    text_layer_set_overflow_mode(s_text_layer, GTextOverflowModeWordWrap);
    layer_add_child(window_layer, text_layer_get_layer(s_text_layer));

    s_index_layer = text_layer_create(GRect(0, h - LAYOUT_INDEX_BOTTOM_MARGIN, w, LAYOUT_INDEX_FONT_HEIGHT));
    text_layer_set_text(s_index_layer, "");
    text_layer_set_text_alignment(s_index_layer, GTextAlignmentCenter);
    text_layer_set_font(s_index_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14));
    layer_add_child(window_layer, text_layer_get_layer(s_index_layer));

    s_bitmap_caret_up = gbitmap_create_with_resource(RESOURCE_ID_ICON_CARET_UP);
    s_bitmap_caret_down = gbitmap_create_with_resource(RESOURCE_ID_ICON_CARET_DOWN);
    s_bitmap_rocket = gbitmap_create_with_resource(RESOURCE_ID_ICON_ROCKET);

    int y_step = h / LAYOUT_NAV_ZONES;
    int y_up = y_step / 2 - LAYOUT_ICON_NAV_SIZE / 2;
    int y_launch = y_step + y_up;
    int y_down = 2 * y_step + y_up;
    int nav_w = w / LAYOUT_NAV_COL_DIVISOR;
    int icon_x = nav_x + (nav_w - LAYOUT_ICON_NAV_SIZE) / 2;

    s_icon_up = bitmap_layer_create(GRect(icon_x, y_up, LAYOUT_ICON_NAV_SIZE, LAYOUT_ICON_NAV_SIZE));
    bitmap_layer_set_bitmap(s_icon_up, s_bitmap_caret_up);
    bitmap_layer_set_compositing_mode(s_icon_up, GCompOpSet);
    bitmap_layer_set_background_color(s_icon_up, GColorClear);
    layer_add_child(window_layer, bitmap_layer_get_layer(s_icon_up));

    s_icon_launch = bitmap_layer_create(GRect(icon_x, y_launch, LAYOUT_ICON_NAV_SIZE, LAYOUT_ICON_NAV_SIZE));
    bitmap_layer_set_bitmap(s_icon_launch, s_bitmap_rocket);
    bitmap_layer_set_compositing_mode(s_icon_launch, GCompOpSet);
    bitmap_layer_set_background_color(s_icon_launch, GColorClear);
    layer_add_child(window_layer, bitmap_layer_get_layer(s_icon_launch));

    s_icon_down = bitmap_layer_create(GRect(icon_x, y_down, LAYOUT_ICON_NAV_SIZE, LAYOUT_ICON_NAV_SIZE));
    bitmap_layer_set_bitmap(s_icon_down, s_bitmap_caret_down);
    bitmap_layer_set_compositing_mode(s_icon_down, GCompOpSet);
    bitmap_layer_set_background_color(s_icon_down, GColorClear);
    layer_add_child(window_layer, bitmap_layer_get_layer(s_icon_down));

    window_set_click_config_provider(window, window_main_click_config_provider);
}

static void window_unload(Window* window) {
    bitmap_layer_destroy(s_icon_up);
    bitmap_layer_destroy(s_icon_down);
    bitmap_layer_destroy(s_icon_launch);
    gbitmap_destroy(s_bitmap_caret_up);
    gbitmap_destroy(s_bitmap_caret_down);
    gbitmap_destroy(s_bitmap_rocket);
    text_layer_destroy(s_text_layer);
    text_layer_destroy(s_index_layer);
    bitmap_layer_destroy(s_icon_layer);
    gbitmap_destroy(s_icon_bitmap);
}

// Request fresh app list from companion on window appear.
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

// Dynamic rendering: adapts layout per basalt/emery, handles empty/loading states, two-pass text height.
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

        int icon_x = (w - LAYOUT_ICON_SIZE) / 2;
        int icon_y = (h - LAYOUT_NAME_FONT_HEIGHT - LAYOUT_ICON_SIZE - LAYOUT_ICON_V_PADDING * 2) / 2;
        layer_set_frame(bitmap_layer_get_layer(s_icon_layer), GRect(icon_x, icon_y, LAYOUT_ICON_SIZE, LAYOUT_ICON_SIZE));

        int text_x, text_width;
        calc_text_frame(&text_x, &text_width);
        layer_set_frame(text_layer_get_layer(s_text_layer), GRect(text_x, 0, text_width, LAYOUT_NAME_MAX_HEIGHT));

        GSize content_size = text_layer_get_content_size(s_text_layer);
        int text_height = content_size.h;
        if (text_height > LAYOUT_NAME_MAX_HEIGHT) {
            text_height = LAYOUT_NAME_MAX_HEIGHT;
        }
        if (text_height < LAYOUT_NAME_FONT_HEIGHT + LAYOUT_NAME_DESCENDER) {
            text_height = LAYOUT_NAME_FONT_HEIGHT + LAYOUT_NAME_DESCENDER;
        }

        int text_y = icon_y + LAYOUT_ICON_SIZE + LAYOUT_ICON_V_PADDING;
        layer_set_frame(text_layer_get_layer(s_text_layer), GRect(text_x, text_y, text_width, text_height));

        if (app_list_has_icon()) {
            memcpy(gbitmap_get_data(s_icon_bitmap), app_list_get_icon(), APP_ICON_SIZE);
            bitmap_layer_set_bitmap(s_icon_layer, s_icon_bitmap);
            layer_set_hidden((Layer *)s_icon_layer, false);
            APP_LOG(APP_LOG_LEVEL_DEBUG, "Icon displayed for: %s", s_display_buf);
        } else {
            layer_set_hidden((Layer *)s_icon_layer, true);
            APP_LOG(APP_LOG_LEVEL_DEBUG, "No icon for: %s", s_display_buf);
        }

        snprintf(s_index_buf, sizeof(s_index_buf), "%d/%d", app_list_get_current_index() + 1, count);
        text_layer_set_text(s_index_layer, s_index_buf);
        layer_set_frame(text_layer_get_layer(s_index_layer), GRect(0, h - LAYOUT_INDEX_BOTTOM_MARGIN, w, LAYOUT_INDEX_FONT_HEIGHT));
        layer_set_hidden((Layer *)s_index_layer, false);

        layer_set_hidden((Layer *)s_icon_up, false);
        layer_set_hidden((Layer *)s_icon_down, false);
        layer_set_hidden((Layer *)s_icon_launch, false);
    } else {
        int h = s_screen_bounds.size.h;

        int text_x, text_width;
        calc_text_frame(&text_x, &text_width);
        layer_set_frame(text_layer_get_layer(s_text_layer), GRect(text_x, 0, text_width, h));

        text_layer_set_text(s_text_layer, packets_is_loading() ? STR_LOADING_MESSAGE : str_empty_message());
        layer_set_hidden((Layer *)s_icon_layer, true);

        GSize content_size = text_layer_get_content_size(s_text_layer);
        int y_empty = (h - content_size.h) / 2;
        layer_set_frame(text_layer_get_layer(s_text_layer), GRect(text_x, y_empty, text_width, content_size.h));

        text_layer_set_text(s_index_layer, "");
        layer_set_hidden((Layer *)s_index_layer, true);
    }
}
