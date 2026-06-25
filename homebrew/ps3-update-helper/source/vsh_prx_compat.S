    .section ".lib.ent.btm","a"
    .align 2
__lib_ent_bottom_marker:
    .long 0

    .section ".lib.stub.btm","a"
    .align 2
__lib_stub_bottom_marker:
    .long 0

    .section ".psp_lib_markimport_","aw"
    .align 3
__PSPIMPORTS__nexus_update_plugin:
    .quad __psp_moduleinfo
    .quad module_start
    .quad module_stop

    .section ".psp_lib_mark_","aw"
    .align 2
__PSPEXP__module_info:
    .long 1
    .long 1
__PSPREN__module_info___psp_moduleinfo:
    .long 1
    .long 0
__PSPEXP__module_start:
    .long 1
    .long 0
__PSPREN__module_start_module_start:
    .long 1
    .long 0
__PSPEXP__module_stop:
    .long 1
    .long 0
__PSPREN__module_stop_module_stop:
    .long 1
    .long 0

    .section ".rodata.sceModuleInfo","a"
    .align 2
    .globl __psp_moduleinfo
__psp_moduleinfo:
    .long 0x00000100
    .ascii "NexusUpdate"
    .zero 18
    .long 0
    .long __libentstart + 4
    .long __libentend
    .long __libstubstart + 4
    .long __libstubend
