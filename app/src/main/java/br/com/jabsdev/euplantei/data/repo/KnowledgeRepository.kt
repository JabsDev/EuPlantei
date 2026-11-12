package br.com.jabsdev.euplantei.data.repo

import android.content.Context
import br.com.jabsdev.euplantei.domain.KnowledgeRetriever
import br.com.jabsdev.euplantei.domain.model.KnowledgeBase
import br.com.jabsdev.euplantei.domain.model.RetrievalResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

class KnowledgeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val retriever = KnowledgeRetriever()
    private val mutex = Mutex()

    @Volatile
    private var cached: KnowledgeBase? = null

    suspend fun load(): KnowledgeBase = withContext(Dispatchers.IO) {
        cached ?: mutex.withLock {
            cached ?: loadFromAssets().also { cached = it }
        }
    }

    suspend fun retrieve(species: String, symptom: String): RetrievalResult = withContext(Dispatchers.Default) {
        val base = load()
        retriever.retrieve(base, species, symptom)
    }

    private fun loadFromAssets(): KnowledgeBase {
        val content = appContext.assets.open("knowledge/plants.json")
            .bufferedReader()
            .use { it.readText() }
        val base = runCatching { json.decodeFromString<KnowledgeBase>(content) }
            .getOrElse { throw IOException("Base de conhecimento inválida", it) }
        if (base.plantas.isEmpty()) throw IOException("Base de conhecimento vazia")
        return base
    }
}
