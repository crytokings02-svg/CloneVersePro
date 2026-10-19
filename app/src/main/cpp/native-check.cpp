#include <jni.h>
#include <fstream>
#include <string>

bool isVirtualContainer() {
    std::ifstream maps("/proc/self/maps");
    std::string line;
    while (std::getline(maps, line)) {
        if (line.find("FCore") != std::string::npos ||
            line.find("virtualapp") != std::string::npos ||
            line.find("libva-") != std::string::npos ||
            line.find("parallel") != std::string::npos) {
            return true;
        }
    }
    return false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_anticlone_security_SecurityManager_checkMapsNative(JNIEnv *env, jobject thiz) {
    return isVirtualContainer() ? JNI_TRUE : JNI_FALSE;
}
