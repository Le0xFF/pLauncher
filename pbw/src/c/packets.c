#include "packets.h"
#include "window_main.h"
#include "app_list.h"
#include <stdlib.h>

static void outbound_sent_handler(DictionaryIterator* iter, void* context) {
}

static void outbound_failed_handler(DictionaryIterator* iter, AppMessageResult reason, void* context) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "Outbound failure: %d", (int)reason);
}

static void inbox_received_handler(DictionaryIterator* iter, void* context) {
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
