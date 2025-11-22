package com.SGSP_ADMIN.controller

import com.SGSP_ADMIN.repository.Product
import com.SGSP_ADMIN.repository.ProductRepository
import com.SGSP_ADMIN.repository.Image
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.Part
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.Locale
import java.util.UUID

@RestController
@RequestMapping("/api/admin/v1/products")
class ProductController(
    private val repo: ProductRepository
) {

    @PostMapping
    fun create(@RequestBody productMono: Mono<Product>): Mono<ResponseEntity<Map<String, Any?>>> {
        return productMono.flatMap { p ->
            if (p.status.isNullOrBlank()) p.status = "DRAFT"
            val now = Instant.now().toString()
            p.createdAt = now
            p.updatedAt = now
            repo.save(p).map { created ->
                val responseData = mapOf(
                    "id" to created.id,
                    "status" to created.status,
                    "name" to created.name,
                    "categoryId" to created.categoryId
                )
                ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "data" to responseData, "error" to null))
            }
        }
    }

    @GetMapping
    fun list(
        @RequestParam("page", required = false, defaultValue = "0") pageParam: Int,
        @RequestParam("size", required = false, defaultValue = "20") sizeParam: Int,
        @RequestParam("status", required = false) status: String?,
        @RequestParam("categoryId", required = false) categoryId: String?,
        @RequestParam("color", required = false) color: String?,
        @RequestParam("search", required = false) search: String?
    ): Mono<ResponseEntity<Map<String, Any?>>> {
        val page = if (pageParam < 0) 0 else pageParam
        val size = if (sizeParam <= 0) 20 else sizeParam

        return repo.findAll().collectList().map { all ->
            val filtered = all.filter { p ->
                var ok = true
                if (!status.isNullOrBlank()) ok = ok && (p.status?.equals(status, ignoreCase = true) ?: false)
                if (!categoryId.isNullOrBlank()) ok = ok && (p.categoryId == categoryId)
                if (!color.isNullOrBlank()) {
                    val attrColor = p.attributes?.get("color")
                    ok = ok && (attrColor != null && attrColor.equals(color, ignoreCase = true))
                }
                if (!search.isNullOrBlank()) {
                    val s = search.lowercase(Locale.getDefault())
                    ok = ok && (
                        (p.name?.lowercase(Locale.getDefault())?.contains(s) ?: false) ||
                        (p.description?.lowercase(Locale.getDefault())?.contains(s) ?: false)
                    )
                }
                ok
            }

            val totalElements = filtered.size
            val totalPages = if (totalElements == 0) 0 else ((totalElements + size - 1) / size)
            val fromIndex = (page * size).coerceAtMost(totalElements)
            val toIndex = ((page * size) + size).coerceAtMost(totalElements)
            val pageContent: List<Product> =
                if (fromIndex >= toIndex) emptyList()
                else filtered.subList(fromIndex, toIndex)

            val contentSummary = pageContent.map { p ->
                val price = (p.pricing?.get("price") as? Number)?.toDouble()
                val stock = (p.inventory?.get("stockQuantity") as? Number)?.toInt()
                val thumbnail = p.images?.firstOrNull()?.url
                mapOf<String, Any?>(
                    "id" to p.id,
                    "name" to p.name,
                    "status" to p.status,
                    "price" to price,
                    "stockQuantity" to stock,
                    "thumbnailUrl" to thumbnail
                )
            }

            val data = mapOf<String, Any?>(
                "content" to contentSummary,
                "page" to page,
                "size" to size,
                "totalElements" to totalElements,
                "totalPages" to totalPages
            )

            ResponseEntity.ok(mapOf<String, Any?>("success" to true, "data" to data, "error" to null))
        }
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): Mono<ResponseEntity<Map<String, Any?>>> {
        return repo.findById(id)
            .map { p -> ResponseEntity.ok(mapOf("success" to true, "data" to p, "error" to null)) }
            .switchIfEmpty(
                Mono.just(
                    ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        mapOf(
                            "success" to false,
                            "data" to null,
                            "error" to mapOf("code" to "PRODUCT_NOT_FOUND", "message" to "Product not found")
                        )
                    )
                )
            )
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody patchMono: Mono<Product>): Mono<ResponseEntity<Map<String, Any?>>> {
        return patchMono.flatMap { patch ->
            repo.findById(id).flatMap { existing ->
                // merge non-null fields from patch into existing
                patch.name?.let { existing.name = it }
                patch.description?.let { existing.description = it }
                patch.categoryId?.let { existing.categoryId = it }
                patch.status?.let { existing.status = it }
                patch.attributes?.let { existing.attributes = it }
                patch.pricing?.let { existing.pricing = it }
                patch.inventory?.let { existing.inventory = it }
                patch.images?.let { existing.images = it }
                existing.updatedAt = Instant.now().toString()
                repo.save(existing).map { updated ->
                    val responseData = mapOf("id" to updated.id, "updatedAt" to updated.updatedAt)
                    ResponseEntity.ok(mapOf("success" to true, "data" to responseData, "error" to null))
                }
            }.switchIfEmpty(
                Mono.just(
                    ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        mapOf(
                            "success" to false,
                            "data" to null,
                            "error" to mapOf("code" to "PRODUCT_NOT_FOUND", "message" to "Product not found")
                        )
                    )
                )
            )
        }
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): Mono<ResponseEntity<Map<String, Any?>>> {
        return repo.findById(id).flatMap { existing ->
            existing.status = "ARCHIVED"
            existing.updatedAt = Instant.now().toString()
            repo.save(existing).map {
                ResponseEntity.ok(mapOf<String, Any?>("success" to true, "data" to null, "error" to null))
            }
        }.switchIfEmpty(
            Mono.just(
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    mapOf<String, Any?>(
                        "success" to false,
                        "data" to null,
                        "error" to mapOf<String, Any?>("code" to "PRODUCT_NOT_FOUND", "message" to "Product not found")
                    )
                )
            )
        )
    }

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: String): Mono<ResponseEntity<Map<String, Any?>>> {
        return repo.findById(id).flatMap { existing ->
            existing.status = "PUBLISHED"
            existing.updatedAt = Instant.now().toString()
            repo.save(existing).map { updated ->
                ResponseEntity.ok(mapOf("success" to true, "data" to mapOf("id" to updated.id, "status" to updated.status), "error" to null))
            }
        }.switchIfEmpty(
            Mono.just(
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    mapOf(
                        "success" to false,
                        "data" to null,
                        "error" to mapOf("code" to "PRODUCT_NOT_FOUND", "message" to "Product not found")
                    )
                )
            )
        )
    }

    @PostMapping("/{id}/unpublish")
    fun unpublish(@PathVariable id: String): Mono<ResponseEntity<Map<String, Any?>>> {
        return repo.findById(id).flatMap { existing ->
            existing.status = "DRAFT"
            existing.updatedAt = Instant.now().toString()
            repo.save(existing).map { updated ->
                ResponseEntity.ok(mapOf("success" to true, "data" to mapOf("id" to updated.id, "status" to updated.status), "error" to null))
            }
        }.switchIfEmpty(
            Mono.just(
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    mapOf(
                        "success" to false,
                        "data" to null,
                        "error" to mapOf("code" to "PRODUCT_NOT_FOUND", "message" to "Product not found")
                    )
                )
            )
        )
    }

    // -------------------- Image upload --------------------
    @PostMapping("/{id}/images")
    fun uploadImages(
        @PathVariable id: String,
        exchange: ServerWebExchange // read multipart data reactively
    ): Mono<ResponseEntity<Map<String, Any?>>> {
        val uploadDir = Paths.get(System.getProperty("user.dir"), "uploads")
        // base URL used in returned image url (adjust if you serve files differently)
        val baseUrl = System.getenv("BASE_URL")?.trimEnd('/') ?: "http://localhost:8080"

        return exchange.multipartData.flatMap { multi: MultiValueMap<String, Part> ->
            val fileParts = (multi["files"] ?: multi["files[]"] ?: emptyList()).mapNotNull { p ->
                if (p is FilePart) p else null
            }
            if (fileParts.isEmpty()) {
                return@flatMap Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    mapOf("success" to false, "data" to null, "error" to mapOf("code" to "NO_FILES", "message" to "No files uploaded"))
                ))
            }

            // ensure upload directory exists
            try {
                Files.createDirectories(uploadDir)
            } catch (_: Exception) {}

            repo.findById(id).flatMap { product ->
                // For each file part, transfer to a file and create Image entry
                val fileMonos = fileParts.mapIndexed { idx, fp ->
                    val imgId = "img_" + UUID.randomUUID().toString().replace("-", "")
                    val ext = fp.filename().substringAfterLast('.', "jpg")
                    val filename = "${imgId}.$ext"
                    val dest = uploadDir.resolve(filename)
                    fp.transferTo(dest).then(Mono.fromCallable {
                        val sortStart = (product.images?.size ?: 0) + 1
                        val image = mapOf(
                            "id" to imgId,
                            "url" to "$baseUrl/uploads/$filename",
                            "sortOrder" to (sortStart + idx)
                        )
                        image
                    })
                }

                Flux.concat(fileMonos).collectList().flatMap { newImagesList ->
                    // convert maps to Image data class instances
                    val currentImages = product.images?.toMutableList() ?: mutableListOf()
                    newImagesList.forEach { imgMap ->
                        @Suppress("UNCHECKED_CAST")
                        val img = Image(
                            id = imgMap["id"] as String?,
                            url = imgMap["url"] as String?,
                            sortOrder = (imgMap["sortOrder"] as? Number)?.toInt()
                        )
                        currentImages.add(img)
                    }
                    product.images = currentImages
                    product.updatedAt = Instant.now().toString()
                    repo.save(product).map { saved ->
                        val responseData = saved.images ?: emptyList<Image>()
                        ResponseEntity.ok(mapOf("success" to true, "data" to responseData, "error" to null))
                    }
                }
            }.switchIfEmpty(
                Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    mapOf("success" to false, "data" to null, "error" to mapOf("code" to "PRODUCT_NOT_FOUND", "message" to "Product not found"))
                ))
            )
        }
    }

    // -------------------- Update image order --------------------
    data class ImageOrderItem(val id: String, val sortOrder: Int)
    data class ImageOrderRequest(val imageOrder: List<ImageOrderItem>)

    @PutMapping("/{id}/images/order")
    fun updateImageOrder(
        @PathVariable id: String,
        @RequestBody reqMono: Mono<ImageOrderRequest>
    ): Mono<ResponseEntity<Map<String, Any?>>> {
        return reqMono.flatMap { req ->
            repo.findById(id).flatMap { product ->
                val orderMap = req.imageOrder.associate { it.id to it.sortOrder }
                val images = product.images?.map { img ->
                    if (img.id != null && orderMap.containsKey(img.id)) {
                        img.copy(sortOrder = orderMap[img.id])
                    } else img
                }?.sortedBy { it.sortOrder ?: Int.MAX_VALUE } ?: emptyList()
                product.images = images
                product.updatedAt = Instant.now().toString()
                repo.save(product).map { saved ->
                    ResponseEntity.ok(mapOf("success" to true, "data" to saved.images, "error" to null))
                }
            }.switchIfEmpty(
                Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    mapOf("success" to false, "data" to null, "error" to mapOf("code" to "PRODUCT_NOT_FOUND", "message" to "Product not found"))
                ))
            )
        }
    }

    // -------------------- Delete image --------------------
    @DeleteMapping("/{id}/images/{imageId}")
    fun deleteImage(
        @PathVariable id: String,
        @PathVariable imageId: String
    ): Mono<ResponseEntity<Map<String, Any?>>> {
        val uploadDir = Paths.get(System.getProperty("user.dir"), "uploads")
        return repo.findById(id).flatMap { product ->
            val existing = product.images ?: emptyList()
            val toRemove = existing.firstOrNull { it.id == imageId }
            if (toRemove == null) {
                return@flatMap Mono.just(
                    ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        mapOf("success" to false, "data" to null, "error" to mapOf("code" to "IMAGE_NOT_FOUND", "message" to "Image not found"))
                    )
                )
            }
            // attempt to delete file if URL points to uploads dir
            try {
                val url = toRemove.url
                if (!url.isNullOrBlank() && url.contains("/uploads/")) {
                    val filename = url.substringAfterLast("/")
                    val path = uploadDir.resolve(filename)
                    try { Files.deleteIfExists(path) } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            val newImages = existing.filterNot { it.id == imageId }
            product.images = newImages
            product.updatedAt = Instant.now().toString()
            repo.save(product).map {
                ResponseEntity.ok<Map<String, Any?>>(mapOf("success" to true, "data" to null, "error" to null))
            }
        }.switchIfEmpty(
            Mono.just(
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    mapOf("success" to false, "data" to null, "error" to mapOf("code" to "PRODUCT_NOT_FOUND", "message" to "Product not found"))
                )
            )
        )
    }
}
