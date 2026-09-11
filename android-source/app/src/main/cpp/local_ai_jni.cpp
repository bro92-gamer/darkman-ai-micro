#include <jni.h>
#include <android/log.h>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

#define TAG "DarkmanLocalAI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model * g_model = nullptr;
static llama_context * g_context = nullptr;
static std::mutex g_mutex;

static std::string jstring_utf8(JNIEnv * env, jstring value) {
    if (!value) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_darkman_LocalAiNative_loadModel(JNIEnv * env, jobject, jstring path, jint contextSize, jint threads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    llama_backend_init();
    llama_model_params modelParams = llama_model_default_params();
    g_model = llama_model_load_from_file(jstring_utf8(env, path).c_str(), modelParams);
    if (!g_model) { LOGE("Could not load GGUF model"); return JNI_FALSE; }
    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = contextSize > 0 ? (uint32_t) contextSize : 2048;
    contextParams.n_batch = 256;
    contextParams.n_threads = threads > 0 ? threads : 2;
    contextParams.n_threads_batch = contextParams.n_threads;
    g_context = llama_init_from_model(g_model, contextParams);
    if (!g_context) { llama_model_free(g_model); g_model = nullptr; LOGE("Could not create llama context"); return JNI_FALSE; }
    LOGI("Loaded Qwen GGUF context=%u threads=%u", contextParams.n_ctx, contextParams.n_threads);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_darkman_LocalAiNative_generate(JNIEnv * env, jobject, jstring prompt, jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context) return env->NewStringUTF("Local AI model is not loaded.");
    const std::string input = jstring_utf8(env, prompt);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const int required = llama_tokenize(vocab, input.c_str(), (int32_t) input.size(), nullptr, 0, true, true);
    if (required <= 0) return env->NewStringUTF("Unable to tokenize prompt.");
    std::vector<llama_token> promptTokens((size_t) required);
    if (llama_tokenize(vocab, input.c_str(), (int32_t) input.size(), promptTokens.data(), required, true, true) < 0) return env->NewStringUTF("Tokenization failed.");
    llama_batch batch = llama_batch_init((int32_t) promptTokens.size(), 0, 1);
    for (size_t i = 0; i < promptTokens.size(); ++i) {
        batch.token[i] = promptTokens[i]; batch.pos[i] = (int32_t) i; batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0; batch.logits[i] = (i + 1 == promptTokens.size());
    }
    const auto start = std::chrono::steady_clock::now();
    if (llama_decode(g_context, batch) != 0) { llama_batch_free(batch); return env->NewStringUTF("Inference decode failed."); }
    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    std::string output;
    int generated = 0;
    const int limit = maxTokens > 0 ? maxTokens : 256;
    while (generated < limit) {
        const llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        const char * piece = llama_vocab_get_text(vocab, token);
        if (piece) output += piece;
        llama_sampler_accept(sampler, token);
        batch.token[0] = token; batch.pos[0] = (int32_t) promptTokens.size() + generated;
        batch.n_seq_id[0] = 1; batch.seq_id[0][0] = 0; batch.logits[0] = true; batch.n_tokens = 1;
        if (llama_decode(g_context, batch) != 0) break;
        generated++;
    }
    const auto elapsed = std::chrono::duration<double>(std::chrono::steady_clock::now() - start).count();
    const double tps = elapsed > 0 ? generated / elapsed : 0.0;
    LOGI("inference tokens=%d seconds=%.3f tokens_per_second=%.2f utf8_bytes=%zu", generated, elapsed, tps, output.size());
    llama_sampler_free(sampler); llama_batch_free(batch);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_darkman_LocalAiNative_freeModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    llama_backend_free();
    LOGI("Local AI model freed");
}
