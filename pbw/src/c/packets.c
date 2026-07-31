#include "packets.h"
#include "window_main.h"
#include "app_list.h"
#include <stdlib.h>
#include <string.h>

static bool s_waiting_for_response = false;
static AppTimer* s_response_timer = NULL;
static uint8_t s_current_transfer_id = 0;
static bool s_loading = false;

#define PERSIST_KEY_VIBRATION_PREF 0x01
static uint8_t s_vibration_pref = VIBE_NONE;

static void save_vibration_pref(uint8_t pref) {
    s_vibration_pref = pref;
    persist_write_int(PERSIST_KEY_VIBRATION_PREF, (int32_t)pref);
    APP_LOG(APP_LOG_LEVEL_INFO, "Saved vibration pref: %d", pref);
}

void load_vibration_pref(void) {
    if (persist_exists(PERSIST_KEY_VIBRATION_PREF)) {
        s_vibration_pref = (uint8_t)persist_read_int(PERSIST_KEY_VIBRATION_PREF);
    } else {
        s_vibration_pref = VIBE_NONE;
    }
    APP_LOG(APP_LOG_LEVEL_INFO, "Loaded vibration pref: %d", s_vibration_pref);
}

uint8_t packets_get_vibration_pref(void) {
    return s_vibration_pref;
}

static void outbound_sent_handler(DictionaryIterator* iter, void* context) {
}

static void outbound_failed_handler(DictionaryIterator* iter, AppMessageResult reason, void* context) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "Outbound failure: %d", (int)reason);
}

static void response_timeout_handler(void* context) {
    s_waiting_for_response = false;
    s_response_timer = NULL;
    s_current_transfer_id = 0;
    s_loading = false;
    APP_LOG(APP_LOG_LEVEL_INFO, "Response timeout, ready to retry");
}

static void handle_phone_welcome(DictionaryIterator* iter) {
    if (s_response_timer) {
        app_timer_cancel(s_response_timer);
        s_response_timer = NULL;
    }
    s_waiting_for_response = false;
    s_current_transfer_id = 0;
    s_loading = false;

    Tuple* t = dict_find(iter, KEY_PROTOCOL_VERSION);
    if (t) {
        uint16_t version = t->value->uint16;
        if (version != 1) {
            APP_LOG(APP_LOG_LEVEL_ERROR, "Protocol mismatch: watch=1 phone=%d", version);
            return;
        }
    }
    APP_LOG(APP_LOG_LEVEL_INFO, "Phone welcome received, protocol v1");
}

static void handle_app_list(DictionaryIterator* iter) {
    Tuple* idTuple = dict_find(iter, KEY_TRANSFER_ID);
    uint8_t transfer_id = idTuple ? idTuple->value->uint8 : 0;

    if (transfer_id < s_current_transfer_id) {
        APP_LOG(APP_LOG_LEVEL_INFO, "Discarding obsolete chunk (id=%d, current=%d)", transfer_id, s_current_transfer_id);
        return;
    }

    if (transfer_id > s_current_transfer_id) {
        s_current_transfer_id = transfer_id;
        app_list_clear();
        APP_LOG(APP_LOG_LEVEL_INFO, "New transfer started (id=%d)", transfer_id);
    }

    Tuple* offsetTuple = dict_find(iter, KEY_OFFSET);
    Tuple* completeTuple = dict_find(iter, KEY_COMPLETION);
    Tuple* countTuple = dict_find(iter, KEY_APP_COUNT);

    bool is_last = (completeTuple && completeTuple->value->uint8 == 1);
    bool is_first = (!offsetTuple || offsetTuple->value->uint16 == 0);

    if (is_first) {
        app_list_clear();
        if (countTuple) {
            uint8_t total = countTuple->value->uint8;
            APP_LOG(APP_LOG_LEVEL_INFO, "App list: %d apps", total);
        }
    }

    Tuple* nameTuple = dict_find(iter, KEY_APP_NAME);
    Tuple* pkgTuple = dict_find(iter, KEY_APP_PACKAGE);

    if (nameTuple && pkgTuple) {
        app_list_add(nameTuple->value->cstring, pkgTuple->value->cstring);
    }

    if (is_last) {
        s_loading = false;
        app_list_reset();
        window_main_update_display();
        APP_LOG(APP_LOG_LEVEL_INFO, "App list complete: %d apps loaded (id=%d)", app_list_get_count(), s_current_transfer_id);
    } else {
        APP_LOG(APP_LOG_LEVEL_DEBUG, "App list chunk received, total so far: %d", app_list_get_count());
    }
}

static void handle_vibration_pref(DictionaryIterator* iter) {
    Tuple* t = dict_find(iter, KEY_VIBRATION_PREF);
    if (t) {
        uint8_t pref = t->value->uint8;
        save_vibration_pref(pref);
    }
}

static void handle_launch_confirm(DictionaryIterator* iter) {
    Tuple* t = dict_find(iter, KEY_LAUNCH_CONFIRM);
    if (!t) {
        return;
    }

    uint8_t confirm = t->value->uint8;

    if (confirm == 1) {
        uint8_t pref = packets_get_vibration_pref();
        if (pref == VIBE_SHORT) {
            vibes_short_pulse();
            APP_LOG(APP_LOG_LEVEL_INFO, "Launch confirm: short pulse");
        } else if (pref == VIBE_LONG) {
            vibes_long_pulse();
            APP_LOG(APP_LOG_LEVEL_INFO, "Launch confirm: long pulse");
        } else if (pref == VIBE_DOUBLE) {
            vibes_double_pulse();
            APP_LOG(APP_LOG_LEVEL_INFO, "Launch confirm: double pulse");
        } else {
            APP_LOG(APP_LOG_LEVEL_INFO, "Launch confirm: no vibration (pref=%d)", pref);
        }
    } else {
        APP_LOG(APP_LOG_LEVEL_INFO, "Launch confirm: failure, no vibration");
    }
}

static void inbox_received_handler(DictionaryIterator* iter, void* context) {
    Tuple* typeTuple = dict_find(iter, KEY_PACKET_TYPE);
    if (!typeTuple) {
        APP_LOG(APP_LOG_LEVEL_ERROR, "Received packet without type");
        return;
    }

    uint8_t packet_type = typeTuple->value->uint8;

    switch (packet_type) {
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
        default:
            APP_LOG(APP_LOG_LEVEL_ERROR, "Unknown packet type: %d", packet_type);
            break;
    }
}

bool packets_is_loading(void) {
    return s_loading;
}

void packets_init(void) {
    app_message_open(1024, 1024);
    app_message_register_outbox_sent(outbound_sent_handler);
    app_message_register_outbox_failed(outbound_failed_handler);
    app_message_register_inbox_received(inbox_received_handler);
}

void packets_deinit(void) {
    app_message_deregister_callbacks();
}

void request_app_list(void) {
    if (!s_waiting_for_response) {
        s_waiting_for_response = true;
        s_loading = true;
        send_watch_welcome();
        s_response_timer = app_timer_register(10 * 1000, response_timeout_handler, NULL);
    }
}

void send_watch_welcome(void) {
    DictionaryIterator* iter;
    if (app_message_outbox_begin(&iter) == APP_MSG_OK) {
        dict_write_uint16(iter, KEY_PROTOCOL_VERSION, 1);
        dict_write_uint8(iter, KEY_PACKET_TYPE, PACKET_TYPE_WATCH_WELCOME);
        app_message_outbox_send();
    }
}

void send_launch_app(uint8_t index) {
    DictionaryIterator* iter;
    if (app_message_outbox_begin(&iter) == APP_MSG_OK) {
        dict_write_uint8(iter, KEY_PACKET_TYPE, PACKET_TYPE_LAUNCH_APP);
        dict_write_uint8(iter, KEY_APP_INDEX, index);
        app_message_outbox_send();
    }
}
