#ifndef PLAUNCHER_WINDOW_MAIN_H
#define PLAUNCHER_WINDOW_MAIN_H

#include <pebble.h>

Window* window_main_create(void);
Window* window_main_get_window(void);
void window_main_update_display(void);

#endif
