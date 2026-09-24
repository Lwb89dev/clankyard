#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int is_elf(const char *path) {
    int fd;
    unsigned char mag[4];
    ssize_t n;
    if (path == NULL) return 0;
    fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return 0;
    n = read(fd, mag, 4);
    close(fd);
    return n == 4 && mag[0] == 0x7f && mag[1] == 'E' && mag[2] == 'L' && mag[3] == 'F';
}

static int should_wrap(const char *path) {
    if (path == NULL || path[0] != '/') return 0;
    if (strncmp(path, "/system/", 8) == 0) return 0;
    if (strncmp(path, "/apex/", 6) == 0) return 0;
    if (strncmp(path, "/vendor/", 8) == 0) return 0;
    if (strncmp(path, "/product/", 9) == 0) return 0;
    return is_elf(path);
}

static char **copy_env_with_preload(char *const envp[]) {
    static const char preload_key[] = "LD_PRELOAD=";
    char *ours = getenv("LD_PRELOAD");
    int count = 0;
    int i;
    char **out;
    int replaced = 0;
    if (envp == NULL) return NULL;
    while (envp[count] != NULL) count++;
    out = calloc((size_t)count + 2, sizeof(char *));
    if (out == NULL) return NULL;
    for (i = 0; i < count; i++) {
        if (ours != NULL && strncmp(envp[i], preload_key, sizeof(preload_key) - 1) == 0) {
            size_t n = sizeof(preload_key) - 1 + strlen(ours) + 1;
            char *row = malloc(n);
            if (row == NULL) continue;
            snprintf(row, n, "LD_PRELOAD=%s", ours);
            out[i] = row;
            replaced = 1;
        } else {
            out[i] = envp[i];
        }
    }
    if (!replaced && ours != NULL) {
        size_t n = sizeof(preload_key) - 1 + strlen(ours) + 1;
        char *row = malloc(n);
        if (row != NULL) {
            snprintf(row, n, "LD_PRELOAD=%s", ours);
            out[count] = row;
        }
    }
    return out;
}

int execve(const char *pathname, char *const argv[], char *const envp[]) {
    static int (*real_execve)(const char *, char *const[], char *const[]) = NULL;
    if (real_execve == NULL) {
        real_execve = (int (*)(const char *, char *const[], char *const[]))dlsym(RTLD_NEXT, "execve");
    }
    if (real_execve == NULL) {
        errno = ENOSYS;
        return -1;
    }
    if (should_wrap(pathname)) {
        int argc = 0;
        int i;
        char **nargv;
        char **nenv;
        while (argv != NULL && argv[argc] != NULL) argc++;
        nargv = calloc((size_t)argc + 3, sizeof(char *));
        if (nargv == NULL) {
            errno = ENOMEM;
            return -1;
        }
        nargv[0] = "/system/bin/linker64";
        nargv[1] = (char *)pathname;
        for (i = 1; i < argc; i++) nargv[i + 1] = argv[i];
        nenv = copy_env_with_preload(envp);
        return real_execve("/system/bin/linker64", nargv, nenv != NULL ? nenv : envp);
    }
    return real_execve(pathname, argv, envp);
}
