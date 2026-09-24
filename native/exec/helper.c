#include <unistd.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: helper <elf> [args...]\n");
        return 2;
    }
    execv(argv[1], argv + 1);
    fprintf(stderr, "execv %s: %s\n", argv[1], strerror(errno));
    return 127;
}
