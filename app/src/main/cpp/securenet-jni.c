/*
 * SecureNet JNI bridge: exposes the hardened C core to Kotlin and re-exports
 * the wireguard-go C ABI (libwgcore.so) as the WgNative Java API.
 *
 * Self-test vectors are the official RFC 8439 ChaCha20 block test and the
 * canonical SHA-256("abc") known answer, so a corrupted build fails loudly
 * at startup rather than silently producing wrong crypto.
 */
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <dlfcn.h>

#include "chacha20.h"
#include "sha256.h"

/* ------------------------------------------------------------------ */
/* ChaCha20 / SHA-256 / key clamping                                   */
/* ------------------------------------------------------------------ */

JNIEXPORT jbyteArray JNICALL
Java_com_securenet_vpn_nativelib_NativeBridge_chacha20Xor(
        JNIEnv *env, jobject thiz,
        jbyteArray jkey, jbyteArray jnonce, jint initial_counter, jbyteArray jdata) {
    (void)thiz;

    jbyte key_buf[CHACHA20_KEY_BYTES];
    jbyte nonce_buf[CHACHA20_NONCE_BYTES];
    jsize data_len = (*env)->GetArrayLength(env, jdata);

    (*env)->GetByteArrayRegion(env, jkey, 0, CHACHA20_KEY_BYTES, key_buf);
    (*env)->GetByteArrayRegion(env, jnonce, 0, CHACHA20_NONCE_BYTES, nonce_buf);

    jbyte *data_buf = (*env)->GetByteArrayElements(env, jdata, NULL);
    jbyteArray out = (*env)->NewByteArray(env, data_len);
    if (out == NULL || data_buf == NULL) {
        if (data_buf != NULL) (*env)->ReleaseByteArrayElements(env, jdata, data_buf, JNI_ABORT);
        return NULL;
    }

    chacha20_xor((const uint8_t *) key_buf,
                 (const uint8_t *) nonce_buf,
                 (uint32_t) initial_counter,
                 (const uint8_t *) data_buf,
                 (size_t) data_len,
                 (uint8_t *) data_buf);

    (*env)->SetByteArrayRegion(env, out, 0, data_len, data_buf);
    (*env)->ReleaseByteArrayElements(env, jdata, data_buf, JNI_ABORT);
    return out;
}

JNIEXPORT jbyteArray JNICALL
Java_com_securenet_vpn_nativelib_NativeBridge_sha256(
        JNIEnv *env, jobject thiz, jbyteArray jdata) {
    (void)thiz;

    jsize data_len = (*env)->GetArrayLength(env, jdata);
    jbyte *data_buf = (*env)->GetByteArrayElements(env, jdata, NULL);
    jbyteArray out = (*env)->NewByteArray(env, SHA256_DIGEST_BYTES);
    uint8_t digest[SHA256_DIGEST_BYTES];

    if (out == NULL || data_buf == NULL) {
        if (data_buf != NULL) (*env)->ReleaseByteArrayElements(env, jdata, data_buf, JNI_ABORT);
        return NULL;
    }

    sha256((const uint8_t *) data_buf, (size_t) data_len, digest);
    (*env)->SetByteArrayRegion(env, out, 0, SHA256_DIGEST_BYTES, (const jbyte *) digest);
    (*env)->ReleaseByteArrayElements(env, jdata, data_buf, JNI_ABORT);
    return out;
}

JNIEXPORT jbyteArray JNICALL
Java_com_securenet_vpn_nativelib_NativeBridge_clampPrivateKey(
        JNIEnv *env, jobject thiz, jbyteArray jkey) {
    (void)thiz;

    jbyte key_buf[32];
    if ((*env)->GetArrayLength(env, jkey) != 32) return NULL;
    (*env)->GetByteArrayRegion(env, jkey, 0, 32, key_buf);

    /* RFC 7748 clamping */
    key_buf[0] = (jbyte)(key_buf[0] & 248);
    key_buf[31] = (jbyte)((key_buf[31] & 127) | 64);

    jbyteArray out = (*env)->NewByteArray(env, 32);
    if (out == NULL) return NULL;
    (*env)->SetByteArrayRegion(env, out, 0, 32, key_buf);
    return out;
}

/* RFC 8439 section 2.3.2 test vector */
static const uint8_t TEST_KEY[32] = {
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
    0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
    0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f
};

static const uint8_t TEST_NONCE[12] = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x4a, 0x00, 0x00, 0x00, 0x00
};

/* First 16 bytes of the expected keystream (RFC 8439 2.3.2). */
static const uint8_t TEST_KEYSTREAM16[16] = {
    0x10, 0xf1, 0xe7, 0xe4, 0xd1, 0x3b, 0x59, 0x15,
    0x50, 0x0f, 0xdd, 0x1f, 0xa3, 0x20, 0x71, 0xc4
};

JNIEXPORT jboolean JNICALL
Java_com_securenet_vpn_nativelib_NativeBridge_selfTest(
        JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;

    uint8_t keystream[CHACHA20_BLOCK_BYTES];
    chacha20_block(TEST_KEY, TEST_NONCE, 1, keystream);
    if (memcmp(keystream, TEST_KEYSTREAM16, 16) != 0) {
        return JNI_FALSE;
    }

    /* SHA-256("abc") known answer */
    static const uint8_t abc[3] = {'a', 'b', 'c'};
    static const uint8_t abc_digest[32] = {
        0xba, 0x78, 0x16, 0xbf, 0x8f, 0x01, 0xcf, 0xea,
        0x41, 0x41, 0x40, 0xde, 0x5d, 0xae, 0x22, 0x23,
        0xb0, 0x03, 0x61, 0xa3, 0x96, 0x17, 0x7a, 0x9c,
        0xb4, 0x10, 0xff, 0x61, 0xf2, 0x00, 0x15, 0xad
    };
    uint8_t digest[SHA256_DIGEST_BYTES];
    sha256(abc, 3, digest);
    if (memcmp(digest, abc_digest, 32) != 0) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_securenet_vpn_nativelib_NativeBridge_nativeVersion(
        JNIEnv *env, jobject thiz) {
    (void)thiz;
    return (*env)->NewStringUTF(env, "securenet-crypto/1.0.0 (chacha20-rfc8439, sha256-fips180-4)");
}

/* ------------------------------------------------------------------ */
/* wireguard-go bridge: dlopen libwgcore.so and re-export as JNI       */
/* ------------------------------------------------------------------ */

typedef int (*wg_turn_on_fn)(const char *ifname, int tun_fd, const char *settings);
typedef void (*wg_turn_off_fn)(int handle);
typedef int (*wg_get_state_fn)(int handle);
typedef const char *(*wg_get_config_fn)(int handle);
typedef const char *(*wg_version_fn)(void);

static void *wg_lib = NULL;
static wg_turn_on_fn p_wgTurnOn = NULL;
static wg_turn_off_fn p_wgTurnOff = NULL;
static wg_get_state_fn p_wgGetState = NULL;
static wg_get_config_fn p_wgGetConfig = NULL;
static wg_version_fn p_wgVersion = NULL;

static int load_wg_symbols_once(void) {
    if (p_wgTurnOn != NULL) return 1;
    if (wg_lib == NULL) {
        wg_lib = dlopen("libwgcore.so", RTLD_NOW | RTLD_LOCAL);
        if (wg_lib == NULL) return 0;
    }
    p_wgTurnOn = (wg_turn_on_fn) dlsym(wg_lib, "wgTurnOn");
    p_wgTurnOff = (wg_turn_off_fn) dlsym(wg_lib, "wgTurnOff");
    p_wgGetState = (wg_get_state_fn) dlsym(wg_lib, "wgGetState");
    p_wgGetConfig = (wg_get_config_fn) dlsym(wg_lib, "wgGetConfig");
    p_wgVersion = (wg_version_fn) dlsym(wg_lib, "wgVersion");
    return p_wgTurnOn != NULL && p_wgTurnOff != NULL;
}

JNIEXPORT jint JNICALL
Java_com_securenet_vpn_nativelib_WgNative_wgTurnOn(
        JNIEnv *env, jobject thiz, jstring jifname, jint tun_fd, jstring jsettings) {
    (void)thiz;
    if (!load_wg_symbols_once()) return -1000;

    const char *ifname = (*env)->GetStringUTFChars(env, jifname, NULL);
    const char *settings = (*env)->GetStringUTFChars(env, jsettings, NULL);
    int handle = p_wgTurnOn(ifname, (int) tun_fd, settings);
    (*env)->ReleaseStringUTFChars(env, jifname, ifname);
    (*env)->ReleaseStringUTFChars(env, jsettings, settings);
    return (jint) handle;
}

JNIEXPORT void JNICALL
Java_com_securenet_vpn_nativelib_WgNative_wgTurnOff(
        JNIEnv *env, jobject thiz, jint handle) {
    (void)env; (void)thiz;
    if (p_wgTurnOff == NULL && !load_wg_symbols_once()) return;
    p_wgTurnOff((int) handle);
}

JNIEXPORT jint JNICALL
Java_com_securenet_vpn_nativelib_WgNative_wgGetState(
        JNIEnv *env, jobject thiz, jint handle) {
    (void)env; (void)thiz;
    if (p_wgGetState == NULL && !load_wg_symbols_once()) return -1;
    return (jint) p_wgGetState((int) handle);
}

JNIEXPORT jstring JNICALL
Java_com_securenet_vpn_nativelib_WgNative_wgGetConfig(
        JNIEnv *env, jobject thiz, jint handle) {
    (void)thiz;
    if (p_wgGetConfig == NULL && !load_wg_symbols_once()) {
        return (*env)->NewStringUTF(env, "");
    }
    const char *config = p_wgGetConfig((int) handle);
    return (*env)->NewStringUTF(env, config == NULL ? "" : config);
}

JNIEXPORT jstring JNICALL
Java_com_securenet_vpn_nativelib_WgNative_wgVersion(
        JNIEnv *env, jobject thiz) {
    (void)thiz;
    if (p_wgVersion == NULL && !load_wg_symbols_once()) {
        return (*env)->NewStringUTF(env, "unavailable");
    }
    const char *version = p_wgVersion();
    return (*env)->NewStringUTF(env, version == NULL ? "unknown" : version);
}
