#include "packets.h"
#include "window_main.h"
#include "app_list.h"
#include <stdlib.h>
#include <string.h>

static void outbound_sent_handler(DictionaryIterator* iter, void* context) {
}

static void outbound_failed_handler(DictionaryIterator* iter, AppMessageResult reason, void* context) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "Outbound failure: %d", (int)reason);
}

static void handle_phone_welcome(DictionaryIterator* iter) {
    Tuple* t = dict_find(iter, 1);
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
    Tuple* offsetTuple = dict_find(iter, 8);
    Tuple* completeTuple = dict_find(iter, 9);
    Tuple* countTuple = dict_find(iter, 3);

    bool is_last = (completeTuple && completeTuple->value->uint8 == 1);
    bool is_first = (!offsetTuple || offsetTuple->value->uint16 == 0);

    if (is_first) {
        app_list_clear();
        if (countTuple) {
            uint8_t total = countTuple->value->uint8;
            APP_LOG(APP_LOG_LEVEL_INFO, "App list: %d apps", total);
        }
    }

    Tuple* nameTuple = dict_find(iter, 4);
    Tuple* pkgTuple = dict_find(iter, 5);

    if (nameTuple && pkgTuple) {
        app_list_add(nameTuple->value->cstring, pkgTuple->value->cstring);
    }

    if (is_last) {
        app_list_reset();
        window_main_update_display();
        APP_LOG(APP_LOG_LEVEL_INFO, "App list complete: %d apps loaded", app_list_get_count());
    } else {
        APP_LOG(APP_LOG_LEVEL_DEBUG, "App list chunk received, total so far: %d", app_list_get_count());
    }
}

static void inbox_received_handler(DictionaryIterator* iter, void* context) {
    Tuple* typeTuple = dict_find(iter, 0);
    if (!typeTuple) {
        APP_LOG(APP_LOG_LEVEL_ERROR, "Received packet without type");
        return;
    }

    uint8_t packet_type = typeTuple->value->uint8;

    switch (packet_type) {
        case 10:  // Phone Welcome
            handle_phone_welcome(iter);
            break;
        case 11:  // App List
            handle_app_list(iter);
            break;
        default:
            APP_LOG(APP_LOG_LEVEL_ERROR, "Unknown packet type: %d", packet_type);
            break;
    }
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

void send_watch_welcome(void) {
    DictionaryIterator* iter;
    if (app_message_outbox_begin(&iter) == APP_MSG_OK) {
        dict_write_uint16(iter, 1, 1);
        dict_write_uint8(iter, 0, 0);
        app_message_outbox_send();
    }
}

void send_launch_app(uint8_t index) {
    DictionaryIterator* iter;
    if (app_message_outbox_begin(&iter) == APP_MSG_OK) {
        dict_write_uint8(iter, 0, 1);
        dict_write_uint8(iter, 2, index);
        app_message_outbox_send();
    }
}
