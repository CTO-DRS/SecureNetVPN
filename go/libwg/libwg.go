// libwg exposes the wireguard-go userspace implementation through a plain
// C ABI (go build -buildmode=c-shared), so it can be dlopen'd from the
// SecureNet C++ JNI layer without gomobile.
//
// The exported functions mirror wireguard-android's GoBackend native API:
//
//   int  wgTurnOn(const char *ifname, int tunFd, const char *settings)
//   void wgTurnOff(int handle)
//   int  wgGetState(int handle)          // 0 down, 1 up
//   const char *wgGetConfig(int handle)  // device.IpcGet dump
//   const char *wgVersion(void)
//
// `settings` uses wireguard-go's device.IpcSet wire format (hex keys):
//   private_key=...\npublic_key=...\nendpoint=host:port\n
//   allowed_ip=0.0.0.0/0\npersistent_keepalive_interval=25\n
package main

import (
        "fmt"
        "sync"

        "golang.zx2c4.com/wireguard/conn"
        "golang.zx2c4.com/wireguard/device"
        "golang.zx2c4.com/wireguard/tun"
)

/*
#include <stdlib.h>
*/
import "C"

type tunnelEntry struct {
        dev   *device.Device
        iface string
}

var (
        tunnelsMutex sync.Mutex
        tunnels      = make(map[int]tunnelEntry)
        nextHandle   = 1
)

func newHandle(dev *device.Device, iface string) int {
        tunnelsMutex.Lock()
        defer tunnelsMutex.Unlock()
        handle := nextHandle
        nextHandle++
        tunnels[handle] = tunnelEntry{dev: dev, iface: iface}
        return handle
}

func lookupHandle(handle int) *tunnelEntry {
        tunnelsMutex.Lock()
        defer tunnelsMutex.Unlock()
        if entry, ok := tunnels[handle]; ok {
                return &entry
        }
        return nil
}

func dropHandle(handle int) *tunnelEntry {
        tunnelsMutex.Lock()
        defer tunnelsMutex.Unlock()
        entry, ok := tunnels[handle]
        if ok {
                delete(tunnels, handle)
        }
        return &entry
}

//export wgTurnOn
func wgTurnOn(ifname *C.char, tunFd C.int, settings *C.char) C.int {
        name := C.GoString(ifname)
        config := C.GoString(settings)

        // The TUN fd comes from Android's VpnService.Builder.establish().
        tunnel, ifaceName, err := tun.CreateUnmonitoredTUNFromFD(int(tunFd))
        if err != nil {
                return C.int(-1)
        }
        _ = ifaceName // Android controls the interface name.

        logger := device.NewLogger(
                device.LogLevelVerbose,
                fmt.Sprintf("(SecureNet/%s) ", name),
        )
        dev := device.NewDevice(tunnel, conn.NewDefaultBind(), logger)

        if err := dev.IpcSet(config); err != nil {
                dev.Close()
                return C.int(-2)
        }

        if err := dev.Up(); err != nil {
                dev.Close()
                return C.int(-3)
        }

        return C.int(newHandle(dev, name))
}

//export wgTurnOff
func wgTurnOff(handle C.int) {
        entry := dropHandle(int(handle))
        if entry != nil && entry.dev != nil {
                entry.dev.Close()
        }
}

//export wgGetState
func wgGetState(handle C.int) C.int {
        entry := lookupHandle(int(handle))
        if entry == nil {
                return C.int(-1)
        }
        return C.int(1) // present in the registry == up (Close removes it)
}

//export wgGetConfig
func wgGetConfig(handle C.int) *C.char {
        entry := lookupHandle(int(handle))
        if entry == nil {
                return C.CString("")
        }
        dump, err := entry.dev.IpcGet()
        if err != nil {
                return C.CString("")
        }
        return C.CString(dump)
}

//export wgVersion
func wgVersion() *C.char {
        return C.CString("wireguard-go 0.0.20230223 (securenet build)")
}

// Required for c-shared builds; never actually runs.
func main() {}
