#ifndef PLAUNCHER_PACKETS_H
#define PLAUNCHER_PACKETS_H

#include <pebble.h>

void packets_init(void);
void packets_deinit(void);
void send_watch_welcome(void);
void send_launch_app(uint8_t index);

#endif
