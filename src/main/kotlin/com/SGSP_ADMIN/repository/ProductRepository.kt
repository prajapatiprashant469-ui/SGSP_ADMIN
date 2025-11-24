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
    var categoryName: String? = null,
    var status: String? = "DRAFT", // DRAFT | PUBLISHED | ARCHIVED
    var attributes: Map<String, String>? = null,
    var pricing: Pricing? = null,
    var inventory: Inventory? = null,
    var images: List<Image>? = null,
    var createdAt: String? = null,
    var updatedAt: String? = null
)

data class Inventory(
    var sku: String? = null,
    var stockQuantity: Int? = null,
    var lowStockThreshold: Int? = null
)

data class Pricing(
    var price: Double? = null,
    var currency: String? = "INR",
    var discountType: String? = "NONE",
    var discountValue: Double? = null
)

interface ProductRepository : ReactiveMongoRepository<Product, String>
