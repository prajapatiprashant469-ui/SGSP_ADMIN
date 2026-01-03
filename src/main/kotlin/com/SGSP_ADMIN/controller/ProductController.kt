package com.SGSP_ADMIN.controller

import com.SGSP_ADMIN.repository.CategoryRepository
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
import org.springframework.data.mongodb.gridfs.ReactiveGridFsTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.bson.types.ObjectId
import java.time.Instant
import java.util.Locale
import java.util.UUID

@RestController
@RequestMapping("/api/admin/v1/products")
class ProductController(
    private val repo: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val gridFsTemplate: ReactiveGridFsTemplate
) {

    @PostMapping
    fun create(@RequestBody productMono: Mono<Product>): Mono<ResponseEntity<Map<String, Any?>>> {

        return productMono.flatMap { p ->

            // Fetch category name from category repository
            categoryRepository.findById(p.categoryId!!)
                .switchIfEmpty(Mono.error(RuntimeException("Category not found")))
                .flatMap { category ->

                    // Set categoryName
                    p.categoryName = category.name

                    // Set timestamps
                    if (p.status.isNullOrBlank()) p.status = "DRAFT"
                    val now = Instant.now().toString()
                    p.createdAt = now
                    p.updatedAt = now

                    repo.save(p).map { created ->

                        val responseData = mapOf(
                            "id" to created.id,
                            "status" to created.status,
                            "name" to created.name,
                            "workerAssigned" to created.workerAssigned,
                            "categoryId" to created.categoryId,
                            "categoryName" to created.categoryName // NEW
                        )

                        ResponseEntity
                            .status(HttpStatus.CREATED)
                            .body(
                                mapOf(
                                    "success" to true,
                                    "data" to responseData,
                                    "error" to null
                                )
                            )
                    }
                }
        }
    }

    @GetMapping
    fun list(
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "20") size: Int,
        @RequestParam("status", required = false) status: String?,
        @RequestParam("categoryId", required = false) categoryId: String?,
        @RequestParam("color", required = false) color: String?,
        @RequestParam("search", required = false) search: String?
    ): Mono<ResponseEntity<Map<String, Any?>>> {

        return repo.findAll()
            .sort(compareByDescending<Product> { it.createdAt ?: "" })
            .filter { p ->
                var ok = true

                if (!status.isNullOrBlank())
                    ok = ok && (p.status.equals(status, true))

                if (!categoryId.isNullOrBlank())
                    ok = ok && (p.categoryId == categoryId)

                if (!color.isNullOrBlank())
                    ok = ok && (p.attributes?.get("color")?.equals(color, true) == true)

                if (!search.isNullOrBlank()) {
                    val s = search.lowercase()
                    ok = ok && (
                            p.name?.lowercase()?.contains(s) == true ||
                                    p.description?.lowercase()?.contains(s) == true
                            )
                }

                ok
            }
            .collectList()
            .flatMap { filtered ->

                val totalElements = filtered.size
                val totalPages = if (totalElements == 0) 0 else ((totalElements + size - 1) / size)

                val fromIndex = (page * size).coerceAtMost(totalElements)
                val toIndex = ((page * size) + size).coerceAtMost(totalElements)

                val pageContent =
                    if (fromIndex >= toIndex) emptyList<Product>()
                    else filtered.subList(fromIndex, toIndex)

                // fetch category names reactively
                Flux.fromIterable(pageContent)
                    .flatMap { p ->
                        categoryRepository.findById(p.categoryId!!)
                            .map {
                                mapOf(
                                    "id" to p.id,
                                    "name" to p.name,
                                    "status" to p.status,
                                    "categoryId" to p.categoryId,
                                    "categoryName" to it.name,
                                    "price" to p.pricing?.price,
                                    "stockQuantity" to p.inventory?.stockQuantity,
                                    "thumbnailUrl" to p.images?.firstOrNull()?.url
                                )
                            }
                            .defaultIfEmpty(
                                mapOf(
                                    "id" to p.id,
                                    "name" to p.name,
                                    "status" to p.status,
                                    "categoryId" to p.categoryId,
                                    "categoryName" to "N/A",
                                    "price" to p.pricing?.price,
                                    "stockQuantity" to p.inventory?.stockQuantity,
                                    "thumbnailUrl" to p.images?.firstOrNull()?.url
                                )
                            )
                    }
                    .collectList()
                    .map { contentSummary ->

                        val data = mapOf(
                            "content" to contentSummary,
                            "page" to page,
                            "size" to size,
                            "totalElements" to totalElements,
                            "totalPages" to totalPages
                        )

                        ResponseEntity.ok(
                            mapOf(
                                "success" to true,
                                "data" to data,
                                "error" to null
                            )
                        )
                    }
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

    /* -------------------- IMAGE UPLOAD (GridFS) -------------------- */
    @PostMapping("/{id}/images")
    fun uploadImages(
        @PathVariable id: String,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Map<String, Any?>>> {

        return exchange.multipartData.flatMap { multi: MultiValueMap<String, Part> ->
            val fileParts = (multi["files"] ?: multi["files[]"] ?: emptyList())
                .filterIsInstance<FilePart>()

            if (fileParts.isEmpty()) {
                return@flatMap Mono.just(
                    ResponseEntity.badRequest().body(
                        mapOf("success" to false, "data" to null, "error" to "No files uploaded")
                    )
                )
            }

            repo.findById(id).flatMap { product ->

                val startOrder = (product.images?.size ?: 0) + 1

                val imageMonos = fileParts.mapIndexed { idx, fp ->
                    gridFsTemplate.store(
                        fp.content(),
                        fp.filename(),
                        fp.headers().contentType.toString(),
                        mapOf("productId" to id)
                    ).map { fileId ->
                        Image(
                            id = fileId.toString(),
                            url = "/api/admin/v1/products/$id/images/$fileId",
                            sortOrder = startOrder + idx
                        )
                    }
                }

                Flux.concat(imageMonos).collectList().flatMap { newImages ->
                    val allImages = (product.images ?: mutableListOf()).toMutableList()
                    allImages.addAll(newImages)
                    product.images = allImages
                    product.updatedAt = Instant.now().toString()

                    repo.save(product).map {
                        ResponseEntity.ok(
                            mapOf("success" to true, "data" to it.images, "error" to null)
                        )
                    }
                }
            }
        }
    }

    /* -------------------- SERVE IMAGE -------------------- */
    @GetMapping("/{productId}/images/{imageId}")
    fun serveImage(
        @PathVariable productId: String,
        @PathVariable imageId: String
    ): Mono<ResponseEntity<ByteArray>> {

        return gridFsTemplate.findOne(
            Query.query(Criteria.where("_id").`is`(ObjectId(imageId)))
        )
            .switchIfEmpty(Mono.error(RuntimeException("Image not found")))
            .flatMap { file ->
                gridFsTemplate.getResource(file)
                    .flatMap { resource ->
                        DataBufferUtils.join(resource.content)
                            .map { dataBuffer ->
                                val bytes = ByteArray(dataBuffer.readableByteCount())
                                dataBuffer.read(bytes)
                                DataBufferUtils.release(dataBuffer)

                                ResponseEntity.ok()
                                    .header(
                                        "Content-Type",
                                        file.metadata?.getString("_contentType") ?: MediaType.IMAGE_JPEG_VALUE
                                    )
                                    .body(bytes)
                            }
                    }
            }
    }



    /* -------------------- DELETE IMAGE -------------------- */
    @DeleteMapping("/{id}/images/{imageId}")
    fun deleteImage(
        @PathVariable id: String,
        @PathVariable imageId: String
    ): Mono<ResponseEntity<Map<String, Any?>>> {

        return repo.findById(id).flatMap { product ->
            val updatedImages = product.images?.filterNot { it.id == imageId } ?: emptyList()
            product.images = updatedImages
            product.updatedAt = Instant.now().toString()

            gridFsTemplate.delete(
                Query.query(Criteria.where("_id").`is`(ObjectId(imageId)))
            ).then(
                repo.save(product).map {
                    ResponseEntity.ok(
                        mapOf("success" to true, "data" to null, "error" to null)
                    )
                }
            )
        }
    }
}
