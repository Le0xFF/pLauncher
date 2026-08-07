/*
 * pLauncher Watchapp — Main window API declarations. Window creation, access, and display update.
 *
 * Author: Le0xFF
 */
#ifndef PLAUNCHER_WINDOW_MAIN_H
#define PLAUNCHER_WINDOW_MAIN_H

#include <pebble.h>

// Buffer size for index display string (e.g. "999/999\0")
#define INDEX_BUFFER_SIZE 32

Window *window_main_create(void);
Window *window_main_get_window(void);
void    window_main_update_display(void);

#endif
