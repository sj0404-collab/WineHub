#include <jni.h>
#include <string>
#include <array>
#include <fstream>
#include <sstream>
#include <cstdio>

static std::string jstringToString(JNIEnv* env, jstring input) {
    const char* chars = env->GetStringUTFChars(input, nullptr);
    std::string out(chars == nullptr ? "" : chars);
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(input, chars);
    }
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winehub_nativebridge_NativeBridge_execute(JNIEnv* env, jobject, jstring command) {
    std::string cmd = jstringToString(env, command);
    std::array<char, 256> buffer{};
    std::string output;

    FILE* pipe = popen(cmd.c_str(), "r");
    if (!pipe) {
        return env->NewStringUTF("failed to execute process");
    }
    while (fgets(buffer.data(), static_cast<int>(buffer.size()), pipe) != nullptr) {
        output.append(buffer.data());
    }
    pclose(pipe);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winehub_nativebridge_NativeBridge_readText(JNIEnv* env, jobject, jstring path) {
    std::string filePath = jstringToString(env, path);
    std::ifstream stream(filePath);
    if (!stream.good()) {
        return env->NewStringUTF("");
    }
    std::stringstream ss;
    ss << stream.rdbuf();
    return env->NewStringUTF(ss.str().c_str());
}
