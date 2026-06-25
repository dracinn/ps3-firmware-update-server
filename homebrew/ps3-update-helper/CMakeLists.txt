cmake_minimum_required(VERSION 3.20)
project(ps3-update-helper C ASM)

include(ps3-self)

set(SERVER_IP "192.168.1.50" CACHE STRING "Desktop app IP address baked into the PS3 helper/plugin")

set(PS3_UPDATE_COMMON_SOURCES
    source/ps3_update_client.c)

add_executable(ps3-update-helper
    source/status_app.c
    ${PS3_UPDATE_COMMON_SOURCES})
target_compile_features(ps3-update-helper PRIVATE c_std_11)
target_compile_options(ps3-update-helper PRIVATE -Wall -Wextra)
target_compile_definitions(ps3-update-helper PRIVATE
    SERVER_IP="${SERVER_IP}"
    PS3_HELPER_USE_PSL1GHT_NET=1)
target_link_libraries(ps3-update-helper PRIVATE
    net sysmodule_stub c_stub sysutil rt lv2 m)

ps3_add_self(ps3-update-helper)
ps3_add_pkg(ps3-update-helper
    TITLE "Nexus PS3 Updater Status"
    APPID "PS3UPD001"
    CONTENTID "UP0001-PS3UPD001_00-PS3UPDATEHELPER0")

add_executable(nexus-update-plugin
    source/vsh_prx_compat.S
    source/plugin.c
    ${PS3_UPDATE_COMMON_SOURCES})
target_compile_features(nexus-update-plugin PRIVATE c_std_11)
target_compile_options(nexus-update-plugin PRIVATE -Wall -Wextra)
target_compile_definitions(nexus-update-plugin PRIVATE
    SERVER_IP="${SERVER_IP}"
    PS3_HELPER_USE_PSL1GHT_NET=1)
target_link_libraries(nexus-update-plugin PRIVATE
    net sysmodule_stub c_stub sysutil rt lv2 m)
target_link_options(nexus-update-plugin PRIVATE
    -nostartfiles
    "${PS3DK}/ppu/lib/lv2-sprx.o"
    "LINKER:-e,module_start")

set(plugin_elf "$<TARGET_FILE:nexus-update-plugin>")
set(plugin_stripped "${CMAKE_CURRENT_SOURCE_DIR}/nexus-update-plugin.elf")
set(plugin_sprx "${CMAKE_CURRENT_SOURCE_DIR}/nexus-update-plugin.sprx")

add_custom_command(TARGET nexus-update-plugin POST_BUILD
    COMMAND "${CMAKE_STRIP}" "${plugin_elf}" -o "${plugin_stripped}"
    COMMAND "${PS3_TOOL_sprxlinker}" ${PS3_SPRXLINKER_FLAGS} "${plugin_stripped}"
    COMMAND "${PS3_TOOL_fself}" "${plugin_stripped}" "${plugin_sprx}"
    BYPRODUCTS "${plugin_stripped}" "${plugin_sprx}"
    COMMENT "ps3-plugin: nexus-update-plugin.sprx"
    VERBATIM)
