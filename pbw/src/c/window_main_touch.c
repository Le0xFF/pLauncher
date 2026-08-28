/*
 * pLauncher Watchapp — Touch input for emery: swipe up/down navigates the app list, a single tap
 * launches the selected app (same packet as the physical SELECT button).
 *
 * Gestures are implemented directly on the raw touch_service event stream (touchdown /
 * position update / liftoff), the same approach used by working emery apps such as touch-tone.
 * The window recognizer API is deliberately NOT used: on the current emery firmware the
 * recognizers attached via window_attach_recognizer never fire even though the raw stream
 * is delivered, so the raw stream is the only reliable input path.
 *
 * On basalt the touch service compiles to SDK no-ops, so the module is simply inert there.
 *
 * Author: Le0xFF
 */
#include "window_main_touch.h"
#include "window_main.h"
#include "app_list.h"
#include "packets.h"

// State for the in-progress gesture. Single-point touch hardware: one finger at a time.
static bool     s_active; // a touchdown has occurred without a matching liftoff
static GPoint   s_down_point;
static uint32_t s_down_time;
static int16_t  s_max_move; // max distance from the touchdown point seen so far

// Milliseconds since UNIX epoch (wraps after ~71 minutes: irrelevant for a tap timeout window).
static uint32_t now_ms(void)
{
    time_t   tloc = 0;
    uint16_t ms   = 0;
    time_ms(&tloc, &ms);
    return ((uint32_t)tloc * 1000u) + (uint32_t)ms;
}

// Track the largest displacement from the touchdown point while the finger is down.
static void track_move(int16_t x, int16_t y)
{
    int16_t dx = x - s_down_point.x;
    int16_t dy = y - s_down_point.y;
    if (dx < 0)
    {
        dx = -dx;
    }
    if (dy < 0)
    {
        dy = -dy;
    }
    int16_t dist = (dx > dy ? dx : dy);
    if (dist > s_max_move)
    {
        s_max_move = dist;
    }
}

// Finish the gesture: classify it as a tap or a vertical swipe and act accordingly.
static void finish_gesture(int16_t lift_x, int16_t lift_y, const TouchEvent *event)
{
    (void)lift_x;
    (void)lift_y;
    if (!s_active)
    {
        return;
    }
    s_active = false;

    // Ignore touches that arrived while the interaction session was inactive (e.g. an idle
    // watchface contact): they must not drive navigation.
    if (event->non_navigational)
    {
        APP_LOG(APP_LOG_LEVEL_DEBUG, "touch ignored (non-navigational)");
        return;
    }

    int16_t  delta_y = lift_y - s_down_point.y;
    uint32_t dt      = now_ms() - s_down_time;

    // Swipe: dominant vertical movement beyond the threshold, either direction.
    if (delta_y <= -TOUCH_SWIPE_MIN_DELTA_PX || delta_y >= TOUCH_SWIPE_MIN_DELTA_PX)
    {
        APP_LOG(APP_LOG_LEVEL_DEBUG, "touch swipe %s (delta.y=%d)", delta_y < 0 ? "up" : "down", delta_y);
        // Guard: ignore navigation while loading.
        if (packets_is_loading())
        {
            return;
        }
        if (delta_y < 0)
        {
            app_list_next();
        }
        else
        {
            app_list_prev();
        }
        window_main_update_display();
        return;
    }

    // Tap: short, still touch (within timeout and movement tolerance).
    if (dt <= TOUCH_TAP_TIMEOUT_MS && s_max_move <= TOUCH_TAP_MAX_MOVE_PX)
    {
        // Guard: ignore launch while loading.
        if (packets_is_loading())
        {
            return;
        }
        if (app_list_get_count() == 0)
        {
            return;
        }
        int index = app_list_get_current_index();
        APP_LOG(APP_LOG_LEVEL_DEBUG, "touch tap: launch index %d", index);
        send_launch_app((uint8_t)index);
    }
    else
    {
        APP_LOG(APP_LOG_LEVEL_DEBUG, "touch: no action (dt=%lu, move=%d, dy=%d)", dt, s_max_move, delta_y);
    }
}

static void touch_handler(const TouchEvent *event, void *context)
{
    (void)context;
    switch (event->type)
    {
    case TouchEvent_Touchdown:
        s_active     = true;
        s_down_point = GPoint(event->x, event->y);
        s_down_time  = now_ms();
        s_max_move   = 0;
        break;

    case TouchEvent_PositionUpdate:
        if (s_active)
        {
            track_move(event->x, event->y);
        }
        break;

    case TouchEvent_Liftoff:
        finish_gesture(event->x, event->y, event);
        break;

    default:
        break;
    }
}

void window_main_touch_init(void)
{
    APP_LOG(APP_LOG_LEVEL_INFO, "touch service enabled: %d", touch_service_is_enabled() ? 1 : 0);
    if (touch_service_is_enabled())
    {
        touch_service_subscribe(touch_handler, NULL);
    }
}

void window_main_touch_deinit(void)
{
    if (touch_service_is_enabled())
    {
        touch_service_unsubscribe();
    }
}