TARGET := ps3-update-helper
BUILD_DIR := build
SOURCE_DIR := source
SERVER_IP ?= 192.168.1.50

PPU_CC ?= ppu-gcc
PPU_STRIP ?= ppu-strip

CFLAGS := -Wall -Wextra -O2 -DSERVER_IP=\"$(SERVER_IP)\" -DPS3_HELPER_USE_PSL1GHT_NET=1
LDFLAGS :=

SOURCES := $(SOURCE_DIR)/main.c
OBJECTS := $(patsubst $(SOURCE_DIR)/%.c,$(BUILD_DIR)/%.o,$(SOURCES))
ELF := $(BUILD_DIR)/$(TARGET).elf

.PHONY: all clean

all: $(ELF)

$(ELF): $(OBJECTS)
	$(PPU_CC) $(OBJECTS) $(LDFLAGS) -o $@
	-$(PPU_STRIP) $@

$(BUILD_DIR)/%.o: $(SOURCE_DIR)/%.c
	mkdir -p $(dir $@)
	$(PPU_CC) $(CFLAGS) -c $< -o $@

clean:
	rm -rf $(BUILD_DIR)
