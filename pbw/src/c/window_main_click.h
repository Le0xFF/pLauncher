/*
 * pLauncher Watchapp — Click configuration provider declaration.
 *
 * Author: Le0xFF
 */
#ifndef PLAUNCHER_WINDOW_MAIN_CLICK_H
#define PLAUNCHER_WINDOW_MAIN_CLICK_H

#include <pebble.h>

// Repeat rate for navigation click handlers (ms)
#define CLICK_REPEAT_INTERVAL_MS 200

void window_main_click_config_provider(void *context);

#endif
