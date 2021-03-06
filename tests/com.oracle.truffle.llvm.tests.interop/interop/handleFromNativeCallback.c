#include <polyglot.h>
#include <truffle.h>

int callbackPointerArgTest(int (*callback)(void *), void *arg);

int callback(void *handle) {
    void *managed = truffle_managed_from_handle(handle);
    return polyglot_as_i32(polyglot_get_member(managed, "valueI"));
}

int testHandleFromNativeCallback(void *managed) {
    void *handle = truffle_handle_for_managed(managed);
    int ret = callbackPointerArgTest(callback, handle);
    truffle_release_handle(handle);
    return ret;
}

int main() {
    return 0;
}
