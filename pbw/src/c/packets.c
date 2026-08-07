/*
 * pLauncher Watchapp — AppMessage packet handling implementation. Manages send/receive, preferences persistence, and protocol state
 * machine.
 *
 * Author: Le0xFF
 */
#include "packets.h"
#include "window_main.h"
#include "app_list.h"
#include <stdlib.h>
#include <string.h>

static bool      s_waiting_for_response = false;
static AppTimer *s_response_timer       = NULL;
static uint8_t   s_current_transfer_id  = 0;
static bool      s_loading              = true;
static AppTimer *s_auto_launch_timer    = NULL;
static bool      s_auto_launch_pending  = false;

#define PERSIST_KEY_VIBRATION_PREF 0x01
static uint8_t s_vibration_pref = VIBE_NONE;

#define PERSIST_KEY_AUTO_CLOSE 0x02
static bool s_auto_close = false;

#define PERSIST_KEY_AUTO_LAUNCH_ENABLED 0x03
static bool s_auto_launch_enabled = false;

#define PERSIST_KEY_AUTO_LAUNCH_TARGET 0x04
static uint8_t s_auto_launch_target = 0;

static void save_vibration_pref(uint8_t pref)
{
    s_vibration_pref = pref;
    persist_write_int(PERSIST_KEY_VIBRATION_PREF, (int32_t)pref);
    APP_LOG(APP_LOG_LEVEL_INFO, "Saved vibration pref: %d", pref);
}

void load_vibration_pref(void)
{
    if (persist_exists(PERSIST_KEY_VIBRATION_PREF))
    {
        s_vibration_pref = (uint8_t)persist_read_int(PERSIST_KEY_VIBRATION_PREF);
    }
    else
    {
        s_vibration_pref = VIBE_NONE;
    }
    APP_LOG(APP_LOG_LEVEL_INFO, "Loaded vibration pref: %d", s_vibration_pref);
}

uint8_t packets_get_vibration_pref(void)
{
    return s_vibration_pref;
}

static void save_auto_close_pref(bool enabled)
{
    s_auto_close = enabled;
    persist_write_int(PERSIST_KEY_AUTO_CLOSE, enabled ? 1 : 0);
    APP_LOG(APP_LOG_LEVEL_INFO, "Saved auto-close pref: %d", enabled ? 1 : 0);
}

void load_auto_close_pref(void)
{
    if (persist_exists(PERSIST_KEY_AUTO_CLOSE))
        s_auto_close = (bool)persist_read_int(PERSIST_KEY_AUTO_CLOSE);
    else
        s_auto_close = false;
    APP_LOG(APP_LOG_LEVEL_INFO, "Loaded auto-close pref: %d", s_auto_close ? 1 : 0);
}

bool packets_get_auto_close(void)
{
    return s_auto_close;
}

static void save_auto_launch_pref(bool enabled)
{
    s_auto_launch_enabled = enabled;
    persist_write_int(PERSIST_KEY_AUTO_LAUNCH_ENABLED, enabled ? 1 : 0);
    APP_LOG(APP_LOG_LEVEL_INFO, "Saved auto-launch pref: %d", enabled ? 1 : 0);
}

static void save_auto_launch_target(uint8_t index)
{
    s_auto_launch_target = index;
    persist_write_int(PERSIST_KEY_AUTO_LAUNCH_TARGET, (int32_t)index);
    APP_LOG(APP_LOG_LEVEL_INFO, "Saved auto-launch target: %d", index);
}

void load_auto_launch_pref(void)
{
    if (persist_exists(PERSIST_KEY_AUTO_LAUNCH_ENABLED))
        s_auto_launch_enabled = (bool)persist_read_int(PERSIST_KEY_AUTO_LAUNCH_ENABLED);
    else
        s_auto_launch_enabled = false;

    if (persist_exists(PERSIST_KEY_AUTO_LAUNCH_TARGET))
        s_auto_launch_target = (uint8_t)persist_read_int(PERSIST_KEY_AUTO_LAUNCH_TARGET);
    else
        s_auto_launch_target = 0;

    APP_LOG(APP_LOG_LEVEL_INFO, "Loaded auto-launch pref: enabled=%d, target=%d", s_auto_launch_enabled ? 1 : 0, s_auto_launch_target);
}

bool packets_get_auto_launch_enabled(void)
{
    return s_auto_launch_enabled;
}

uint8_t packets_get_auto_launch_target(void)
{
    return s_auto_launch_target;
}

// Deferred auto-launch: checks enabled flag, target validity, and dispatches launch.
static void try_auto_launch(void)
{
    if (s_auto_launch_enabled && s_auto_launch_target < (uint8_t)app_list_get_count())
    {
        app_list_set_current_index(s_auto_launch_target);
        window_main_update_display();
        APP_LOG(APP_LOG_LEVEL_INFO, "Auto-launching app at index %d", s_auto_launch_target);
        send_launch_app(s_auto_launch_target);
    }
    s_auto_launch_pending = false;
}

// 500ms delay timer to avoid conflicts with app opening.
static void auto_launch_timer_handler(void *context)
{
    s_auto_launch_timer = NULL;
    if (s_auto_launch_pending)
    {
        try_auto_launch();
    }
}

static void schedule_auto_launch(void)
{
    if (s_auto_launch_timer)
    {
        app_timer_cancel(s_auto_launch_timer);
        s_auto_launch_timer = NULL;
    }
    s_auto_launch_pending = true;
    s_auto_launch_timer   = app_timer_register(AUTO_LAUNCH_DELAY_MS, auto_launch_timer_handler, NULL);
    APP_LOG(APP_LOG_LEVEL_INFO, "Auto-launch scheduled, waiting for prefs...");
}

static void cancel_auto_launch(void)
{
    if (s_auto_launch_timer)
    {
        app_timer_cancel(s_auto_launch_timer);
        s_auto_launch_timer = NULL;
    }
    s_auto_launch_pending = false;
}

static void refresh_auto_launch_timer(void)
{
    if (s_auto_launch_pending)
    {
        cancel_auto_launch();
        schedule_auto_launch();
    }
}

static void handle_auto_launch_pref(DictionaryIterator *iter)
{
    Tuple *t = dict_find(iter, KEY_AUTO_LAUNCH_ENABLED);
    if (t)
    {
        bool enabled = (t->value->uint8 == 1);
        save_auto_launch_pref(enabled);
    }

    refresh_auto_launch_timer();
}

static void handle_auto_launch_target(DictionaryIterator *iter)
{
    Tuple *t = dict_find(iter, KEY_AUTO_LAUNCH_TARGET);
    if (t)
    {
        uint8_t index = t->value->uint8;
        save_auto_launch_target(index);
    }

    refresh_auto_launch_timer();
}

static void handle_auto_close_pref(DictionaryIterator *iter)
{
    Tuple *t = dict_find(iter, KEY_AUTO_CLOSE);
    if (t)
    {
        bool enabled = (t->value->uint8 == 1);
        save_auto_close_pref(enabled);
    }
}

// Reset send state and cancel response timeout on successful outbound.
static void outbound_sent_handler(DictionaryIterator *iter, void *context) {}

// Fallback on outbound failure; clears state to allow retry.
static void outbound_failed_handler(DictionaryIterator *iter, AppMessageResult reason, void *context)
{
    APP_LOG(APP_LOG_LEVEL_ERROR, "Outbound failure: %d", (int)reason);
}

// 10-second timeout handler; resets state and allows retry.
static void response_timeout_handler(void *context)
{
    s_waiting_for_response = false;
    s_response_timer       = NULL;
    s_current_transfer_id  = 0;
    s_loading              = false;
    cancel_auto_launch();
    APP_LOG(APP_LOG_LEVEL_INFO, "Response timeout, ready to retry");
}

// Validate protocol version from phone and reset waiting state.
static void handle_phone_welcome(DictionaryIterator *iter)
{
    if (s_response_timer)
    {
        app_timer_cancel(s_response_timer);
        s_response_timer = NULL;
    }
    s_waiting_for_response = false;
    s_current_transfer_id  = 0;

    Tuple *t = dict_find(iter, KEY_PROTOCOL_VERSION);
    if (t)
    {
        uint16_t version = t->value->uint16;
        if (version != 1)
        {
            APP_LOG(APP_LOG_LEVEL_ERROR, "Protocol mismatch: watch=1 phone=%d", version);
            return;
        }
    }
    APP_LOG(APP_LOG_LEVEL_INFO, "Phone welcome received, protocol v1");
}

// Receive chunked app list; deduplicate by transfer ID, parse fields, clear on new transfer.
static void handle_app_list(DictionaryIterator *iter)
{
    Tuple  *idTuple     = dict_find(iter, KEY_TRANSFER_ID);
    uint8_t transfer_id = idTuple ? idTuple->value->uint8 : 0;

    int16_t diff = (int16_t)((int8_t)s_current_transfer_id - (int8_t)transfer_id);

    if (diff > 0)
    {
        APP_LOG(APP_LOG_LEVEL_INFO, "Discarding obsolete chunk (id=%d, current=%d)", transfer_id, s_current_transfer_id);
        return;
    }

    if (diff < 0)
    {
        s_current_transfer_id = transfer_id;
        s_loading             = true;
        app_list_clear();
        window_main_update_display();
        APP_LOG(APP_LOG_LEVEL_INFO, "New transfer started (id=%d)", transfer_id);
    }

    Tuple *offsetTuple   = dict_find(iter, KEY_OFFSET);
    Tuple *completeTuple = dict_find(iter, KEY_COMPLETION);
    Tuple *countTuple    = dict_find(iter, KEY_APP_COUNT);

    bool is_last  = (completeTuple && completeTuple->value->uint8 == 1);
    bool is_first = (!offsetTuple || offsetTuple->value->uint16 == 0);

    if (is_first)
    {
        app_list_clear();
        if (countTuple)
        {
            uint8_t total = countTuple->value->uint8;
            APP_LOG(APP_LOG_LEVEL_INFO, "App list: %d apps", total);
        }
    }

    Tuple *nameTuple = dict_find(iter, KEY_APP_NAME);
    Tuple *pkgTuple  = dict_find(iter, KEY_APP_PACKAGE);
    Tuple *iconTuple = dict_find(iter, KEY_APP_ICON);

    if (nameTuple && pkgTuple)
    {
        if (iconTuple)
        {
            const uint8_t *iconData = iconTuple->value->data;
            uint16_t       iconLen  = iconTuple->length;
            APP_LOG(APP_LOG_LEVEL_DEBUG, "App icon received: len=%d, expected=%d", iconLen, APP_ICON_SIZE);
            app_list_add(nameTuple->value->cstring, pkgTuple->value->cstring, iconData, iconLen);
        }
        else
        {
            app_list_add(nameTuple->value->cstring, pkgTuple->value->cstring, NULL, 0);
        }
    }

    if (is_last)
    {
        if (s_auto_launch_enabled && s_auto_launch_target < (uint8_t)app_list_get_count())
        {
            app_list_set_current_index(s_auto_launch_target);
        }
        else
        {
            app_list_reset();
        }

        window_main_update_display();
        s_loading = false;
        APP_LOG(APP_LOG_LEVEL_INFO, "App list complete: %d apps loaded (id=%d)", app_list_get_count(), s_current_transfer_id);

        schedule_auto_launch();
    }
    else
    {
        APP_LOG(APP_LOG_LEVEL_DEBUG, "App list chunk received, total so far: %d", app_list_get_count());
    }
}

static void handle_vibration_pref(DictionaryIterator *iter)
{
    Tuple *t = dict_find(iter, KEY_VIBRATION_PREF);
    if (t)
    {
        uint8_t pref = t->value->uint8;
        save_vibration_pref(pref);
    }
}

// Close watch app after vibration duration has elapsed.
static void auto_close_timer_handler(void *context)
{
    exit_reason_set(APP_EXIT_ACTION_PERFORMED_SUCCESSFULLY);
    window_stack_pop_all(false);
}

// Trigger vibration per preference and schedule auto-close timer if enabled.
static void handle_launch_confirm(DictionaryIterator *iter)
{
    Tuple *t = dict_find(iter, KEY_LAUNCH_CONFIRM);
    if (!t)
    {
        return;
    }

    uint8_t confirm = t->value->uint8;

    if (confirm == LAUNCH_CONFIRM_SUCCESS)
    {
        uint8_t pref = packets_get_vibration_pref();
        if (pref == VIBE_SHORT)
        {
            vibes_short_pulse();
            APP_LOG(APP_LOG_LEVEL_INFO, "Launch confirm: short pulse");
        }
        else if (pref == VIBE_LONG)
        {
            vibes_long_pulse();
            APP_LOG(APP_LOG_LEVEL_INFO, "Launch confirm: long pulse");
        }
        else if (pref == VIBE_DOUBLE)
        {
            vibes_double_pulse();
            APP_LOG(APP_LOG_LEVEL_INFO, "Launch confirm: double pulse");
        }
        else
        {
            APP_LOG(APP_LOG_LEVEL_INFO, "Launch confirm: no vibration (pref=%d)", pref);
        }

        if (packets_get_auto_close())
        {
            APP_LOG(APP_LOG_LEVEL_INFO, "Auto-close enabled, scheduling close after vibration");
            uint32_t vibe_duration = 0;
            if (pref == VIBE_SHORT)
            {
                vibe_duration = VIBE_SHORT_DURATION_MS;
            }
            else if (pref == VIBE_LONG)
            {
                vibe_duration = VIBE_LONG_DURATION_MS;
            }
            else if (pref == VIBE_DOUBLE)
            {
                vibe_duration = VIBE_DOUBLE_DURATION_MS;
            }
            app_timer_register(vibe_duration, auto_close_timer_handler, NULL);
        }
    }
    else
    {
        APP_LOG(APP_LOG_LEVEL_INFO, "Launch confirm: failure, no vibration");
    }
}

// Dispatch incoming packets to the appropriate handler by type.
static void inbox_received_handler(DictionaryIterator *iter, void *context)
{
    Tuple *typeTuple = dict_find(iter, KEY_PACKET_TYPE);
    if (!typeTuple)
    {
        APP_LOG(APP_LOG_LEVEL_ERROR, "Received packet without type");
        return;
    }

    uint8_t packet_type = typeTuple->value->uint8;

    switch (packet_type)
    {
    case PACKET_TYPE_PHONE_WELCOME:
        handle_phone_welcome(iter);
        break;
    case PACKET_TYPE_APP_LIST:
        handle_app_list(iter);
        break;
    case PACKET_TYPE_LAUNCH_CONFIRM:
        handle_launch_confirm(iter);
        break;
    case PACKET_TYPE_VIBRATION_PREF:
        handle_vibration_pref(iter);
        break;
    case PACKET_TYPE_AUTO_CLOSE_PREF:
        handle_auto_close_pref(iter);
        break;
    case PACKET_TYPE_AUTO_LAUNCH_PREF:
        handle_auto_launch_pref(iter);
        break;
    case PACKET_TYPE_AUTO_LAUNCH_TARGET:
        handle_auto_launch_target(iter);
        break;
    default:
        APP_LOG(APP_LOG_LEVEL_ERROR, "Unknown packet type: %d", packet_type);
        break;
    }
}

bool packets_is_loading(void)
{
    return s_loading;
}

void packets_init(void)
{
    app_message_open(APP_MESSAGE_BUFFER_SIZE, APP_MESSAGE_BUFFER_SIZE);
    app_message_register_outbox_sent(outbound_sent_handler);
    app_message_register_outbox_failed(outbound_failed_handler);
    app_message_register_inbox_received(inbox_received_handler);
}

void packets_deinit(void)
{
    app_message_deregister_callbacks();
}

void request_app_list(void)
{
    if (!s_waiting_for_response)
    {
        s_waiting_for_response = true;
        s_loading              = true;
        send_watch_welcome();
        s_response_timer = app_timer_register(RESPONSE_TIMEOUT_MS, response_timeout_handler, NULL);
    }
}

void send_watch_welcome(void)
{
    DictionaryIterator *iter;
    if (app_message_outbox_begin(&iter) == APP_MSG_OK)
    {
        dict_write_uint16(iter, KEY_PROTOCOL_VERSION, 1);
        dict_write_uint8(iter, KEY_PACKET_TYPE, PACKET_TYPE_WATCH_WELCOME);
        dict_write_uint8(iter, KEY_DISPLAY_TYPE, PBL_IF_COLOR_ELSE(1, 0));
        APP_LOG(APP_LOG_LEVEL_INFO, "Watch welcome sent: display_type=%d", PBL_IF_COLOR_ELSE(1, 0));
        app_message_outbox_send();
    }
}

void send_launch_app(uint8_t index)
{
    DictionaryIterator *iter;
    if (app_message_outbox_begin(&iter) == APP_MSG_OK)
    {
        dict_write_uint8(iter, KEY_PACKET_TYPE, PACKET_TYPE_LAUNCH_APP);
        dict_write_uint8(iter, KEY_APP_INDEX, index);
        app_message_outbox_send();
    }
}
