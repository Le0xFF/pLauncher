/*
 * pLauncher Watchapp — Touch gesture constants. Initial values, to be tuned on device.
 *
 * Author: Le0xFF
 */
#ifndef PLAUNCHER_WINDOW_MAIN_TOUCH_H
#define PLAUNCHER_WINDOW_MAIN_TOUCH_H

#include <pebble.h>

// Minimum |delta.y| (px) between touchdown and liftoff for the touch to count as a swipe navigation.
// Initial estimate, not tuned on device yet.
#define TOUCH_SWIPE_MIN_DELTA_PX 40

// Maximum finger movement (px) between touchdown and liftoff for the touch to still count as a tap.
// Initial estimate, not tuned on device yet.
#define TOUCH_TAP_MAX_MOVE_PX 16

// Maximum time window (ms) between touchdown and liftoff for the touch to count as a tap.
// Initial estimate, not tuned on device yet.
#define TOUCH_TAP_TIMEOUT_MS 300

void window_main_touch_init(void);
void window_main_touch_deinit(void);

#endif