package com.SGSP_ADMIN.controller

import com.SGSP_ADMIN.repository.Product
import com.SGSP_ADMIN.repository.ProductRepository
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux
import java.time.Instant
import com.mongodb.client.result.UpdateResult

data class PricingRequest(
    val price: Double? = null,
    val currency: String? = null,
    val discountType: String? = null,
    val discountValue: Double? = null
)

data class InventoryRequest(
    val stockQuantity: Int? = null,
    val lowStockThreshold: Int? = null,
    val sku: String? = null
)

@RestController
class ProductInventoryController(
    private val repo: ProductRepository,
    private val mongo: ReactiveMongoTemplate
) {
    // 6.1 Update pricing (partial)
    @PutMapping("/api/admin/v1/products/{id}/pricing")
    fun updatePricing(@PathVariable id: String, @RequestBody reqMono: Mono<PricingRequest>): Mono<ResponseEntity<Map<String, Any?>>> {
        return reqMono.flatMap { req ->
            val query = Query(Criteria.where("_id").`is`(id))
            val update = Update()
            req.price?.let { update.set("pricing.price", it) }
            req.currency?.let { update.set("pricing.currency", it) }
            req.discountType?.let { update.set("pricing.discountType", it) }
            req.discountValue?.let { update.set("pricing.discountValue", it) }
            update.set("updatedAt", Instant.now().toString())

            mongo.updateFirst(query, update, Product::class.java)
                .flatMap { res: UpdateResult ->
                    if (res.matchedCount == 0L) {
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("success" to false, "data" to null, "error" to mapOf("code" to "PRODUCT_NOT_FOUND", "message" to "Product not found"))))
                    } else {
                        mongo.findOne(query, Product::class.java)
                            .map { saved ->
                                ResponseEntity.ok(mapOf("success" to true, "data" to mapOf("id" to saved.id, "pricing" to saved.pricing), "error" to null))
                            }
                    }
                }
        }
    }

    // 6.2 Update inventory (partial)
    @PutMapping("/api/admin/v1/products/{id}/inventory")
    fun updateInventory(@PathVariable id: String, @RequestBody reqMono: Mono<InventoryRequest>): Mono<ResponseEntity<Map<String, Any?>>> {
        return reqMono.flatMap { req ->
            val query = Query(Criteria.where("_id").`is`(id))
            val update = Update()
            req.stockQuantity?.let { update.set("inventory.stockQuantity", it) }
            req.lowStockThreshold?.let { update.set("inventory.lowStockThreshold", it) }
            req.sku?.let { update.set("inventory.sku", it) }
            update.set("updatedAt", Instant.now().toString())

            mongo.updateFirst(query, update, Product::class.java)
                .flatMap { res: UpdateResult ->
                    if (res.matchedCount == 0L) {
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("success" to false, "data" to null, "error" to mapOf("code" to "PRODUCT_NOT_FOUND", "message" to "Product not found"))))
                    } else {
                        mongo.findOne(query, Product::class.java)
                            .map { saved ->
                                ResponseEntity.ok(mapOf("success" to true, "data" to mapOf("id" to saved.id, "inventory" to saved.inventory), "error" to null))
                            }
                    }
                }
        }
    }

    // 6.3 List low-stock items
    @GetMapping("/api/admin/v1/inventory/low-stock")
    fun lowStock(): Mono<ResponseEntity<Map<String, Any?>>> {
        return repo.findAll()
            .filter { p ->
                p.inventory?.let { inv ->
                    val sq = inv.stockQuantity
                    val lt = inv.lowStockThreshold
                    sq != null && lt != null && sq <= lt
                } ?: false
            }
            .map { p ->
                mapOf("productId" to p.id, "name" to p.name, "stockQuantity" to p.inventory?.stockQuantity)
            }
            .collectList()
            .map { list ->
                ResponseEntity.ok(mapOf("success" to true, "data" to list, "error" to null))
            }
    }

    // NEW: 6.3b Low-stock summary endpoint
    @GetMapping("/api/admin/v1/inventory/low-stock-summary")
    fun lowStockSummary(): Mono<ResponseEntity<Map<String, Any?>>> {
        return repo.findAll()
            .filter { p ->
                p.inventory?.let { inv ->
                    val sq = inv.stockQuantity
                    val lt = inv.lowStockThreshold
                    sq != null && lt != null && sq <= lt
                } ?: false
            }
            .map { p ->
                mapOf("productId" to p.id, "name" to p.name, "stockQuantity" to p.inventory?.stockQuantity)
            }
            .collectList()
            .map { list ->
                val data = mapOf(
                    "totalLowStock" to list.size,
                    "items" to list
                )
                ResponseEntity.ok(mapOf("success" to true, "data" to data, "error" to null))
            }
    }
}
