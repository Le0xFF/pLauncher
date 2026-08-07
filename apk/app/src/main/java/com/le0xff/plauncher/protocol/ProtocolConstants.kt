package com.le0xff.plauncher.protocol

// AppMessage dictionary keys
const val KEY_PACKET_TYPE: UInt = 0u
const val KEY_PROTOCOL_VERSION: UInt = 1u
const val KEY_APP_INDEX: UInt = 2u
const val KEY_APP_COUNT: UInt = 3u
const val KEY_APP_NAME: UInt = 4u
const val KEY_APP_PACKAGE: UInt = 5u
const val KEY_TRANSFER_ID: UInt = 6u
const val KEY_OFFSET: UInt = 8u
const val KEY_COMPLETION: UInt = 9u
const val KEY_LAUNCH_CONFIRM: UInt = 10u
const val KEY_VIBRATION_PREF: UInt = 11u
const val KEY_AUTO_CLOSE: UInt = 12u
const val KEY_AUTO_LAUNCH_ENABLED: UInt = 13u
const val KEY_AUTO_LAUNCH_TARGET: UInt = 14u
const val KEY_DISPLAY_TYPE: UInt = 15u
const val KEY_APP_ICON: UInt = 16u

// Packet types
const val PACKET_TYPE_WATCH_WELCOME: Int = 0
const val PACKET_TYPE_LAUNCH_APP: Int = 1
const val PACKET_TYPE_PHONE_WELCOME: Int = 10
const val PACKET_TYPE_APP_LIST: Int = 11
const val PACKET_TYPE_LAUNCH_CONFIRM: Int = 12
const val PACKET_TYPE_VIBRATION_PREF: Int = 13
const val PACKET_TYPE_AUTO_CLOSE_PREF: Int = 14
const val PACKET_TYPE_AUTO_LAUNCH_PREF: Int = 15
const val PACKET_TYPE_AUTO_LAUNCH_TARGET: Int = 16

// Transfer ID mask
const val TRANSFER_ID_MASK: UInt = 0xFFu
