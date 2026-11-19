#define _GNU_SOURCE
#include <arpa/inet.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <unistd.h>

typedef int (*connect_fn)(int, const struct sockaddr *, socklen_t);
typedef ssize_t (*sendto_fn)(int, const void *, size_t, int, const struct sockaddr *, socklen_t);

static int is_loopback(const struct sockaddr *address) {
    if (address->sa_family == AF_INET) {
        const struct sockaddr_in *ipv4 = (const struct sockaddr_in *)address;
        return (ntohl(ipv4->sin_addr.s_addr) >> 24) == 127;
    }
    if (address->sa_family == AF_INET6) {
        const struct sockaddr_in6 *ipv6 = (const struct sockaddr_in6 *)address;
        return IN6_IS_ADDR_LOOPBACK(&ipv6->sin6_addr);
    }
    return address->sa_family == AF_UNIX;
}

static int reject_non_loopback(const struct sockaddr *address) {
    if (address == NULL || is_loopback(address)) return 0;

    const char *log_path = getenv("FOCUSDESKTOP_PRIVACY_AUDIT_LOG");
    if (log_path != NULL) {
        int log_fd = open(log_path, O_WRONLY | O_CREAT | O_APPEND, 0600);
        if (log_fd >= 0) {
            char entry[96];
            int length = snprintf(entry, sizeof(entry), "Blocked outbound socket (family=%d)\n", address->sa_family);
            if (length > 0) write(log_fd, entry, (size_t)length);
            close(log_fd);
        }
    }
    errno = EPERM;
    return 1;
}

int connect(int socket_fd, const struct sockaddr *address, socklen_t address_length) {
    static connect_fn real_connect;
    if (real_connect == NULL) {
        real_connect = (connect_fn)dlsym(RTLD_NEXT, "connect");
        if (real_connect == NULL) {
            errno = ENOSYS;
            return -1;
        }
    }

    if (reject_non_loopback(address)) return -1;
    return real_connect(socket_fd, address, address_length);
}

ssize_t sendto(int socket_fd, const void *buffer, size_t length, int flags,
               const struct sockaddr *destination, socklen_t destination_length) {
    static sendto_fn real_sendto;
    if (real_sendto == NULL) {
        real_sendto = (sendto_fn)dlsym(RTLD_NEXT, "sendto");
        if (real_sendto == NULL) {
            errno = ENOSYS;
            return -1;
        }
    }
    if (reject_non_loopback(destination)) return -1;
    return real_sendto(socket_fd, buffer, length, flags, destination, destination_length);
}
