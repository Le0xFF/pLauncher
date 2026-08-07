/*
 * pLauncher Watchapp — Communication protocol definitions. Packet types, AppMessage keys, and public API declarations.
 *
 * Author: Le0xFF
 */
#ifndef PLAUNCHER_PACKETS_H
#define PLAUNCHER_PACKETS_H

#include <pebble.h>

// Packet types
#define PACKET_TYPE_WATCH_WELCOME      0
#define PACKET_TYPE_LAUNCH_APP         1
#define PACKET_TYPE_PHONE_WELCOME      10
#define PACKET_TYPE_APP_LIST           11
#define PACKET_TYPE_LAUNCH_CONFIRM     12
#define PACKET_TYPE_VIBRATION_PREF     13
#define PACKET_TYPE_AUTO_CLOSE_PREF    14
#define PACKET_TYPE_AUTO_LAUNCH_PREF   15
#define PACKET_TYPE_AUTO_LAUNCH_TARGET 16

// AppMessage keys
#define KEY_PACKET_TYPE         0
#define KEY_PROTOCOL_VERSION    1
#define KEY_APP_INDEX           2
#define KEY_APP_COUNT           3
#define KEY_APP_NAME            4
#define KEY_APP_PACKAGE         5
#define KEY_TRANSFER_ID         6
#define KEY_OFFSET              8
#define KEY_COMPLETION          9
#define KEY_LAUNCH_CONFIRM      10
#define KEY_VIBRATION_PREF      11
#define KEY_AUTO_CLOSE          12
#define KEY_AUTO_LAUNCH_ENABLED 13
#define KEY_AUTO_LAUNCH_TARGET  14
#define KEY_DISPLAY_TYPE        15
#define KEY_APP_ICON            16

// Vibration preference values
#define VIBE_NONE   0
#define VIBE_SHORT  1
#define VIBE_LONG   2
#define VIBE_DOUBLE 3

// AppMessage buffer size
#define APP_MESSAGE_BUFFER_SIZE 2048

// Response timeout after watch welcome (ms)
#define RESPONSE_TIMEOUT_MS (10 * 1000)

// Auto-launch delay after app list received (ms)
#define AUTO_LAUNCH_DELAY_MS 500

// Vibration durations for auto-close timer (ms)
#define VIBE_SHORT_DURATION_MS  250
#define VIBE_LONG_DURATION_MS   500
#define VIBE_DOUBLE_DURATION_MS 300

// Launch confirm success value
#define LAUNCH_CONFIRM_SUCCESS 1

bool    packets_is_loading(void);
void    packets_init(void);
void    packets_deinit(void);
void    send_watch_welcome(void);
void    send_launch_app(uint8_t index);
void    request_app_list(void);
void    load_vibration_pref(void);
uint8_t packets_get_vibration_pref(void);
void    load_auto_close_pref(void);
bool    packets_get_auto_close(void);
void    load_auto_launch_pref(void);
bool    packets_get_auto_launch_enabled(void);
uint8_t packets_get_auto_launch_target(void);

#endif
