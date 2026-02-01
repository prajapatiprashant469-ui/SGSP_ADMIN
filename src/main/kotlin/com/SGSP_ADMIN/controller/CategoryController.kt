package com.SGSP_ADMIN.controller

import com.SGSP_ADMIN.repository.Category
import com.SGSP_ADMIN.repository.CategoryRepository
import com.SGSP_ADMIN.repository.ProductRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux
import java.time.Instant

data class CategoryRequest(val name: String, val slug: String, val parentId: String? = null, val description: String? = null)

@RestController
@RequestMapping("/api/admin/v1/categories")
class CategoryController(
    private val repo: CategoryRepository,
    private val productRepo: ProductRepository
) {

    // 5.1 List categories (exclude archived)
    @GetMapping
    fun list(): Mono<ResponseEntity<Map<String, Any?>>> {
        return repo.findAll()
            .filter { !it.archived }
            .collectList()
            .map { list ->
                ResponseEntity.ok(mapOf("success" to true, "data" to list, "error" to null))
            }
    }

    // 5.2 Create category
    @PostMapping
    fun create(@RequestBody reqMono: Mono<CategoryRequest>): Mono<ResponseEntity<Map<String, Any?>>> {
        return reqMono.flatMap { req ->
            val now = Instant.now().toString()
            val cat = Category(
                id = null,
                name = req.name,
                slug = req.slug,
                parentId = req.parentId,
                description = req.description,
                archived = false,
                createdAt = now,
                updatedAt = now
            )
            repo.save(cat).map { created ->
                ResponseEntity.status(HttpStatus.CREATED).body(
                    mapOf("success" to true, "data" to created, "error" to null)
                )
            }
        }
    }

    // 5.3 Update category
    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody reqMono: Mono<CategoryRequest>): Mono<ResponseEntity<Map<String, Any?>>> {
        return reqMono.flatMap { req ->
            repo.findById(id).flatMap { existing ->
                existing.name = req.name
                existing.slug = req.slug
                existing.parentId = req.parentId
                existing.description = req.description
                existing.updatedAt = Instant.now().toString()
                repo.save(existing).map { saved ->
                    ResponseEntity.ok(mapOf("success" to true, "data" to saved, "error" to null))
                }
            }.switchIfEmpty(
                Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    mapOf("success" to false, "data" to null, "error" to mapOf("code" to "CATEGORY_NOT_FOUND", "message" to "Category not found"))
                ))
            )
        }
    }

    // 5.4 Delete category (soft-delete). Prevent if any active product references it.
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): Mono<ResponseEntity<Map<String, Any?>>> {
        return productRepo.findAll()
            .filter { p -> p.categoryId == id && (p.status == null || p.status != "ARCHIVED") }
            .hasElements()
            .flatMap { inUse ->
                if (inUse) {
                    val body: Map<String, Any?> = mapOf(
                        "success" to false,
                        "data" to null,
                        "error" to mapOf<String, Any?>(
                            "code" to "CATEGORY_IN_USE",
                            "message" to "Category is used by existing products"
                        )
                    )
                    Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body))
                } else {
                    repo.findById(id).flatMap { existing ->
                        existing.archived = true
                        existing.updatedAt = Instant.now().toString()
                        repo.save(existing).map {
                            val body: Map<String, Any?> = mapOf(
                                "success" to true,
                                "data" to null,
                                "error" to null
                            )
                            ResponseEntity.ok(body)
                        }
                    }.switchIfEmpty(
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                            mapOf<String, Any?>(
                                "success" to false,
                                "data" to null,
                                "error" to mapOf<String, Any?>(
                                    "code" to "CATEGORY_NOT_FOUND",
                                    "message" to "Category not found"
                                )
                            )
                        ))
                    )
                }
            }
    }
}
