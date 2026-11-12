#!/usr/bin/env python3
"""Static ARM EABI binary: create a uinput device named virtual-remote and sleep."""

BASE = 0x10000  # file offset 0 is mapped here

words = []
labels = {}
fixups = []  # ("ldr", idx, rd, label) or ("b", idx, cond, label)


def emit(w):
    words.append(w & 0xFFFFFFFF)
    return len(words) - 1


def lab(name):
    labels[name] = len(words) * 4


def mov_imm(rd, imm):
    assert 0 <= imm <= 255, imm
    emit(0xE3A00000 | (rd << 12) | imm)


def mov_reg(rd, rm):
    emit(0xE1A00000 | (rd << 12) | rm)


def add_imm(rd, rn, imm):
    assert 0 <= imm <= 255
    emit(0xE2800000 | (rn << 16) | (rd << 12) | imm)


def sub_imm(rd, rn, imm):
    assert 0 <= imm <= 255
    emit(0xE2400000 | (rn << 16) | (rd << 12) | imm)


def cmp_imm(rn, imm):
    assert 0 <= imm <= 255
    emit(0xE3500000 | (rn << 16) | imm)


def swi():
    emit(0xEF000000)


def ldr_lab(rd, name):
    idx = emit(0)
    fixups.append(("ldr", idx, rd, name))


def b_cond(cond, name):
    idx = emit(0)
    fixups.append(("b", idx, cond, name))


def str_sp(rt, off):
    assert off % 4 == 0 and 0 <= off < 4096
    emit(0xE58D0000 | (rt << 12) | off)


def strh_sp(rt, off):
    # strh rt, [sp, #off]  off must be multiple of... strh immediate is 8-bit
    assert 0 <= off <= 255
    emit(0xE1CD00B0 | (rt << 12) | ((off & 0xF0) << 4) | (off & 0x0F))


# ---- code ----
lab("start")

# fd = open("/dev/uinput", O_WRONLY)
ldr_lab(0, "path_uinput")
mov_imm(1, 1)
mov_imm(2, 0)
mov_imm(7, 5)
swi()
mov_reg(4, 0)
cmp_imm(4, 0)
b_cond(0xB, "exit_fail")  # blt

# UI_SET_EVBIT EV_KEY and EV_SYN
mov_reg(0, 4)
ldr_lab(1, "ui_set_evbit")
mov_imm(2, 1)
mov_imm(7, 54)
swi()
mov_reg(0, 4)
ldr_lab(1, "ui_set_evbit")
mov_imm(2, 0)
mov_imm(7, 54)
swi()

# KEY bits 1..247, including KEY_POWER (116)
mov_imm(5, 1)
lab("keyloop")
mov_reg(0, 4)
ldr_lab(1, "ui_set_keybit")
mov_reg(2, 5)
mov_imm(7, 54)
swi()
add_imm(5, 5, 1)
cmp_imm(5, 248)
b_cond(0xB, "keyloop")

# uinput_setup on the stack, 96 zero bytes
sub_imm(13, 13, 96)
mov_imm(6, 0)
for off in range(0, 96, 4):
    str_sp(6, off)
# id.bustype = BUS_VIRTUAL (6)
mov_imm(6, 6)
strh_sp(6, 0)
# name at offset 8: "virtual-remote"
# little-endian words
name_words = [0x74726976, 0x2D6C6175, 0x6F6D6572, 0x00006574]
for i, w in enumerate(name_words):
    # load word via literal
    ldr_lab(6, "nw%d" % i)
    str_sp(6, 8 + i * 4)

# ioctl(fd, UI_DEV_SETUP, &setup)
mov_reg(0, 4)
ldr_lab(1, "ui_dev_setup")
mov_reg(2, 13)  # sp
mov_imm(7, 54)
swi()
mov_reg(8, 0)  # save setup rc

# ioctl(fd, UI_DEV_CREATE)
mov_reg(0, 4)
ldr_lab(1, "ui_dev_create")
mov_imm(2, 0)
mov_imm(7, 54)
swi()
mov_reg(9, 0)  # save create rc

# write a one-line status to the log
ldr_lab(0, "path_log")
mov_imm(1, 1)       # O_WRONLY
# O_CREAT|O_TRUNC added below via orr of encoded immediates is messy.
# open with O_WRONLY only if we create the file from outside. We'll
# create it in the shell first. Just write.
mov_imm(2, 0)
mov_imm(7, 5)
swi()
mov_reg(6, 0)  # log fd
# build "Sxxxxxxxx Cxxxxxxxx\n" is hard. Write raw 8 bytes of the two rcs.
# store r8 and r9 to a small buffer on the remaining stack... stack was
# subtracted by 96 and still holds the setup. Use a spot past the struct:
# write(log, &rcs, 8) where rcs are stored at sp+92? only 96 bytes, offsets 0-92 used.
# store at a new stack slot
sub_imm(13, 13, 16)
str_sp(8, 0)
str_sp(9, 4)
mov_imm(3, 0x0A)
str_sp(3, 8)
mov_reg(0, 6)
mov_reg(1, 13)
mov_imm(2, 9)
mov_imm(7, 4)  # write
swi()
add_imm(13, 13, 16)

# sleep forever so the device stays
lab("sleep")
ldr_lab(0, "timespec")
mov_imm(1, 0)
mov_imm(7, 162)  # nanosleep
swi()
b_cond(0xE, "sleep")

lab("exit_fail")
mov_imm(0, 1)
mov_imm(7, 1)  # exit
swi()

# ---- data ----
def align4():
    while (len(words) * 4) % 4:
        emit(0)


def emit_bytes(data):
    data = data + b"\x00" * ((4 - len(data) % 4) % 4)
    for i in range(0, len(data), 4):
        emit(int.from_bytes(data[i:i + 4], "little"))


lab("path_uinput_str")
emit_bytes(b"/dev/uinput\x00")
lab("path_log_str")
emit_bytes(b"/data/local/tmp/vremote.log\x00")
lab("timespec_data")
emit(3600)  # tv_sec
emit(0)     # tv_nsec
lab("pool")
# pointers and constants, filled as their own labels
lab("path_uinput")
fixups.append(("abs", len(words), "path_uinput_str"))
emit(0)
lab("path_log")
fixups.append(("abs", len(words), "path_log_str"))
emit(0)
lab("timespec")
fixups.append(("abs", len(words), "timespec_data"))
emit(0)
lab("ui_set_evbit")
emit(0x40045564)
lab("ui_set_keybit")
emit(0x40045565)
lab("ui_dev_setup")
emit(0x405C5503)
lab("ui_dev_create")
emit(0x00005501)
for i, w in enumerate(name_words):
    lab("nw%d" % i)
    emit(w)

code_vaddr = BASE  # words start at file offset equal to... we'll place code at file offset 0x80
# Rebuild: ELF headers occupy 0x80, code is words placed at file offset 0x80.
# vaddr of word 0 = BASE + 0x80
CODE_OFF = 0x80
code_base = BASE + CODE_OFF

for kind in fixups:
    pass

resolved = list(words)
for item in fixups:
    if item[0] == "ldr":
        _, idx, rd, name = item
        pc = code_base + idx * 4 + 8
        target = code_base + labels[name]
        off = target - pc
        if not (0 <= off < 4096):
            raise SystemExit("ldr off %s %d" % (name, off))
        resolved[idx] = 0xE59F0000 | (rd << 12) | off
    elif item[0] == "b":
        _, idx, cond, name = item
        pc = code_base + idx * 4 + 8
        target = code_base + labels[name]
        imm = (target - pc) >> 2
        if not (-0x800000 <= imm < 0x800000):
            raise SystemExit("branch %s" % name)
        resolved[idx] = (cond << 28) | 0x0A000000 | (imm & 0x00FFFFFF)
    elif item[0] == "abs":
        _, idx, name = item
        resolved[idx] = code_base + labels[name]

blob = b"".join(w.to_bytes(4, "little") for w in resolved)

# ELF
def elf():
    e = bytearray(CODE_OFF + len(blob))
    e[0:4] = b"\x7fELF"
    e[4] = 1  # 32-bit
    e[5] = 1  # little
    e[6] = 1
    # e_type ET_EXEC = 2, e_machine EM_ARM = 40
    e[16:18] = (2).to_bytes(2, "little")
    e[18:20] = (40).to_bytes(2, "little")
    e[20:24] = (1).to_bytes(4, "little")
    entry = code_base + labels["start"]
    e[24:28] = entry.to_bytes(4, "little")
    e[28:32] = (52).to_bytes(4, "little")  # e_phoff
    e[32:36] = (0).to_bytes(4, "little")
    e[36:40] = (0x05000000).to_bytes(4, "little")  # flags EF_ARM_ABI_VER5? 0x05000000 is EABI v5
    e[40:42] = (52).to_bytes(2, "little")  # e_ehsize
    e[42:44] = (32).to_bytes(2, "little")  # e_phentsize
    e[44:46] = (1).to_bytes(2, "little")   # e_phnum
    # program header at 52
    ph = 52
    def pw(off, val):
        e[ph + off:ph + off + 4] = val.to_bytes(4, "little")
    pw(0, 1)  # PT_LOAD
    pw(4, 0)  # offset
    pw(8, BASE)  # vaddr
    pw(12, BASE)  # paddr
    filesz = CODE_OFF + len(blob)
    pw(16, filesz)
    pw(20, filesz)
    pw(24, 7)  # RWX
    pw(28, 0x1000)
    e[CODE_OFF:CODE_OFF + len(blob)] = blob
    return bytes(e)

out = r"C:\Users\amolp\Downloads\cleanyourtv\tmp_apk\atvremote\vremote"
open(out, "wb").write(elf())
print("wrote", out, "entry", hex(code_base + labels["start"]), "size", CODE_OFF + len(blob))
