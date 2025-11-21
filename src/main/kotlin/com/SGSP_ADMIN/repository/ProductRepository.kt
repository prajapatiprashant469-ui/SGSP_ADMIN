package com.SGSP_ADMIN.repository

import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Image(
    val id: String? = null,
    val url: String? = null,
    val sortOrder: Int? = null
)

data class Product(
    var id: String? = null,
    var name: String? = null,
    var description: String? = null,
    var categoryId: String? = null,
    var status: String? = "DRAFT", // DRAFT | PUBLISHED | ARCHIVED
    var attributes: Map<String, String>? = null,
    var pricing: Map<String, Any>? = null,
    var inventory: Map<String, Any>? = null,
    var images: List<Image>? = null,
    var createdAt: String? = null,
    var updatedAt: String? = null
)

@Repository
class ProductRepository {
    private val store = ConcurrentHashMap<String, Product>()

    fun create(product: Product): Mono<Product> {
        val id = product.id ?: "p_" + UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val toSave = product.copy(id = id, createdAt = now, updatedAt = now)
        store[id] = toSave
        return Mono.just(toSave)
    }

    fun findById(id: String): Mono<Product> =
        Mono.justOrEmpty(store[id])

    fun findAll(): Flux<Product> =
        Flux.fromIterable(store.values)

    fun update(id: String, patch: Product): Mono<Product> {
        val existing = store[id] ?: return Mono.empty()
        // Merge non-null fields from patch into existing
        patch.name?.let { existing.name = it }
        patch.description?.let { existing.description = it }
        patch.categoryId?.let { existing.categoryId = it }
        patch.status?.let { existing.status = it }
        patch.attributes?.let { existing.attributes = it }
        patch.pricing?.let { existing.pricing = it }
        patch.inventory?.let { existing.inventory = it }
        patch.images?.let { existing.images = it }
        existing.updatedAt = Instant.now().toString()
        store[id] = existing
        return Mono.just(existing)
    }

    fun delete(id: String): Mono<Boolean> =
        Mono.just(store.remove(id) != null)
}
