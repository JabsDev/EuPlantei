#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {

constexpr const char * TAG = "EuPlanteiLlama";
constexpr int32_t N_BATCH = 256;
constexpr int32_t PENALTY_LAST_N = 64;

std::mutex g_mutex;
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
llama_batch g_batch{};
int32_t g_n_ctx = 4096;
std::atomic<bool> g_cancel{false};
bool g_backend_ready = false;

void android_log_callback(enum ggml_log_level level, const char * text, void * /*user_data*/) {
    int priority = ANDROID_LOG_DEBUG;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: priority = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  priority = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_INFO:  priority = ANDROID_LOG_INFO;  break;
        default: break;
    }
    __android_log_write(priority, TAG, text);
}

void ensure_backend() {
    if (g_backend_ready) return;
    llama_log_set(android_log_callback, nullptr);
    llama_backend_init();
    g_backend_ready = true;
    __android_log_print(ANDROID_LOG_INFO, TAG, "llama.cpp backend inicializado");
}

void unload_internal() {
    if (g_batch.token != nullptr) {
        llama_batch_free(g_batch);
        g_batch = {};
    }
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_n_ctx = 4096;
}

bool is_valid_utf8(const std::string & value) {
    const auto * bytes = reinterpret_cast<const unsigned char *>(value.data());
    const auto * end = bytes + value.size();
    while (bytes < end) {
        int num = 1;
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        if (bytes + num > end) return false;
        for (int i = 1; i < num; ++i) {
            if ((bytes[i] & 0xC0) != 0x80) return false;
        }
        bytes += num;
    }
    return true;
}

std::string token_to_piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(256);
    int32_t count = llama_token_to_piece(
            vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false);
    if (count < 0) {
        buffer.resize(static_cast<size_t>(-count) + 1);
        count = llama_token_to_piece(
                vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false);
        if (count < 0) return {};
    }
    return std::string(buffer.data(), static_cast<size_t>(count));
}

/** Formata o prompt com o template embutido no GGUF (model-agnostic, sem ChatML hardcoded). */
std::string apply_chat_template(const std::string & prompt) {
    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    if (tmpl == nullptr || tmpl[0] == '\0') return prompt;

    llama_chat_message message{"user", prompt.c_str()};
    std::vector<char> buffer(prompt.size() * 2 + 1024);
    int32_t needed = llama_chat_apply_template(
            tmpl, &message, 1, true, buffer.data(), static_cast<int32_t>(buffer.size()));
    if (needed > static_cast<int32_t>(buffer.size())) {
        buffer.resize(static_cast<size_t>(needed) + 1);
        needed = llama_chat_apply_template(
                tmpl, &message, 1, true, buffer.data(), static_cast<int32_t>(buffer.size()));
    }
    if (needed <= 0) return prompt;
    return std::string(buffer.data(), static_cast<size_t>(needed));
}

int32_t decode_tokens(
        const std::vector<llama_token> & tokens,
        llama_pos start_position,
        bool compute_last_logit) {
    int32_t offset = 0;
    const int32_t total = static_cast<int32_t>(tokens.size());
    while (offset < total) {
        const int32_t count = std::min(N_BATCH, total - offset);
        g_batch.n_tokens = count;
        for (int32_t i = 0; i < count; ++i) {
            const int32_t index = offset + i;
            g_batch.token[index - offset] = tokens[index];
            g_batch.pos[index - offset] = start_position + index;
            g_batch.n_seq_id[index - offset] = 1;
            g_batch.seq_id[index - offset][0] = 0;
            const bool last = compute_last_logit && index == total - 1;
            g_batch.logits[index - offset] = last ? 1 : 0;
        }
        if (llama_decode(g_context, g_batch) != 0) {
            return -4;
        }
        offset += count;
    }
    return 0;
}

int32_t decode_single(llama_token token, llama_pos position) {
    g_batch.n_tokens = 1;
    g_batch.token[0] = token;
    g_batch.pos[0] = position;
    g_batch.n_seq_id[0] = 1;
    g_batch.seq_id[0][0] = 0;
    g_batch.logits[0] = 1;
    if (llama_decode(g_context, g_batch) != 0) return -4;
    return 0;
}

bool emit_token(JNIEnv * env, jobject callback, jmethodID method, const std::string & text) {
    jstring value = env->NewStringUTF(text.c_str());
    if (value == nullptr) return false;
    env->CallVoidMethod(callback, method, value);
    env->DeleteLocalRef(value);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        return false;
    }
    return true;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_br_com_jabsdev_euplantei_ml_NativeLlamaBridge_nativeLoadModel(
        JNIEnv * env, jobject /*thiz*/, jstring model_path, jint n_ctx, jint n_threads) {
    ensure_backend();
    std::lock_guard<std::mutex> lock(g_mutex);
    g_cancel.store(false);
    unload_internal();

    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 1;
    const std::string model_path_string(path);
    env->ReleaseStringUTFChars(model_path, path);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;

    g_model = llama_model_load_from_file(model_path_string.c_str(), model_params);
    if (g_model == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Falha ao carregar GGUF: %s", model_path_string.c_str());
        return 1;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(n_ctx > 0 ? n_ctx : 4096);
    context_params.n_batch = N_BATCH;
    context_params.n_ubatch = N_BATCH;
    context_params.n_threads = n_threads > 0 ? n_threads : 4;
    context_params.n_threads_batch = n_threads > 0 ? n_threads : 4;
    context_params.no_perf = false;

    g_context = llama_init_from_model(g_model, context_params);
    if (g_context == nullptr) {
        unload_internal();
        return 1;
    }
    g_batch = llama_batch_init(N_BATCH, 0, 1);
    g_n_ctx = static_cast<int32_t>(llama_n_ctx(g_context));
    __android_log_print(
            ANDROID_LOG_INFO, TAG, "Modelo carregado (n_ctx=%d, threads=%d)", g_n_ctx,
            context_params.n_threads);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_br_com_jabsdev_euplantei_ml_NativeLlamaBridge_nativeGenerate(
        JNIEnv * env,
        jobject /*thiz*/,
        jstring prompt,
        jfloat temperature,
        jfloat top_p,
        jint top_k,
        jfloat repeat_penalty,
        jint max_tokens,
        jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr || g_context == nullptr || g_batch.token == nullptr) return -1;
    if (callback == nullptr) return -5;

    g_cancel.store(false);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    if (vocab == nullptr) return -1;

    const char * raw_prompt = env->GetStringUTFChars(prompt, nullptr);
    if (raw_prompt == nullptr) return -5;
    std::string prompt_text(raw_prompt);
    env->ReleaseStringUTFChars(prompt, raw_prompt);

    // 1. Aplica o template do próprio modelo.
    const std::string formatted = apply_chat_template(prompt_text);

    // 2. Tokeniza incluindo tokens especiais do template.
    int32_t token_count = -llama_tokenize(
            vocab, formatted.c_str(), static_cast<int32_t>(formatted.size()), nullptr, 0, true, true);
    if (token_count <= 0) return -3;
    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    const int32_t real_count = llama_tokenize(
            vocab, formatted.c_str(), static_cast<int32_t>(formatted.size()), tokens.data(),
            token_count, true, true);
    if (real_count < 0) return -3;
    tokens.resize(static_cast<size_t>(real_count));

    const int32_t reserve = std::max(1, static_cast<int32_t>(max_tokens));
    if (static_cast<int32_t>(tokens.size()) + reserve + 8 > g_n_ctx) {
        __android_log_print(
                ANDROID_LOG_WARN, TAG, "Prompt grande: %zu tokens + %d reservados para n_ctx=%d",
                tokens.size(), reserve, g_n_ctx);
        return -2;
    }

    // 3. Limpa o KV cache e processa o prompt.
    llama_memory_clear(llama_get_memory(g_context), true);
    llama_pos position = 0;
    int32_t result = decode_tokens(tokens, position, true);
    if (result != 0) return result;
    position += static_cast<llama_pos>(tokens.size());

    // 4. Sampler chain com os parâmetros do produto.
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    if (sampler == nullptr) return -5;
    llama_sampler_chain_add(
            sampler,
            llama_sampler_init_penalties(
                    llama_vocab_n_tokens(vocab), PENALTY_LAST_N, repeat_penalty, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    jclass callback_class = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
    if (on_token == nullptr) {
        env->ExceptionClear();
        llama_sampler_free(sampler);
        return -5;
    }

    std::string cached;
    int32_t generated = 0;
    for (int32_t i = 0; i < reserve; ++i) {
        if (g_cancel.load()) {
            result = 2;
            break;
        }
        llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (token < 0) {
            result = -4;
            break;
        }
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }
        llama_sampler_accept(sampler, token);

        cached += token_to_piece(vocab, token);
        if (is_valid_utf8(cached)) {
            if (!cached.empty()) {
                if (!emit_token(env, callback, on_token, cached)) {
                    result = -6;
                    break;
                }
                ++generated;
                cached.clear();
            }
        }

        result = decode_single(token, position);
        if (result != 0) break;
        ++position;
    }

    if (result == 0 || result == 2) {
        if (!cached.empty() && is_valid_utf8(cached)) {
            emit_token(env, callback, on_token, cached);
        }
    }
    if (result == 0) result = generated;

    llama_sampler_free(sampler);
    env->DeleteLocalRef(callback_class);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_jabsdev_euplantei_ml_NativeLlamaBridge_nativeCancel(JNIEnv *, jobject /*thiz*/) {
    g_cancel.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_jabsdev_euplantei_ml_NativeLlamaBridge_nativeUnload(JNIEnv *, jobject /*thiz*/) {
    g_cancel.store(true);
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_internal();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_br_com_jabsdev_euplantei_ml_NativeLlamaBridge_nativeIsLoaded(JNIEnv *, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return (g_model != nullptr && g_context != nullptr) ? JNI_TRUE : JNI_FALSE;
}
