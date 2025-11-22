package com.SGSP_ADMIN.repository

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository

@Document(collection = "products")
data class Image(
    val id: String? = null,
    val url: String? = null,
    val sortOrder: Int? = null
)

@Document(collection = "products")
data class Product(
    @Id var id: String? = null,
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

interface ProductRepository : ReactiveMongoRepository<Product, String>
