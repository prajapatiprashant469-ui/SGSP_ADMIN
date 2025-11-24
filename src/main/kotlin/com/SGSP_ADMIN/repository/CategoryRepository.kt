package com.SGSP_ADMIN.repository

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository

@Document(collection = "categories")
data class Category(
    @Id var id: String? = null,
    var name: String,
    var slug: String,
    var parentId: String? = null,
    var description: String? = null,
    var archived: Boolean = false,
    var createdAt: String? = null,
    var updatedAt: String? = null
)

interface CategoryRepository : ReactiveMongoRepository<Category, String>
