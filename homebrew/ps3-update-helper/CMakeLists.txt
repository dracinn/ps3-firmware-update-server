cmake_minimum_required(VERSION 3.20)
project(ps3-update-helper C)

include(ps3-self)

set(SERVER_IP "192.168.1.50" CACHE STRING "Desktop app IP address baked into the PS3 helper")

add_executable(ps3-update-helper source/main.c)
target_compile_features(ps3-update-helper PRIVATE c_std_11)
target_compile_options(ps3-update-helper PRIVATE -Wall -Wextra)
target_compile_definitions(ps3-update-helper PRIVATE
    SERVER_IP="${SERVER_IP}"
    PS3_HELPER_USE_PSL1GHT_NET=1)
target_link_libraries(ps3-update-helper PRIVATE
    net sysmodule_stub c_stub sysutil rt lv2 m)

ps3_add_self(ps3-update-helper)
ps3_add_pkg(ps3-update-helper
    TITLE "PS3 Update Helper"
    APPID "PS3UPD001"
    CONTENTID "UP0001-PS3UPD001_00-PS3UPDATEHELPER0")
