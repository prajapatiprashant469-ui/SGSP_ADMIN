package com.SGSP_ADMIN.controller

import com.SGSP_ADMIN.repository.CategoryRepository
import com.SGSP_ADMIN.repository.Product
import com.SGSP_ADMIN.repository.ProductRepository
import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.Aggregation.*
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/api/admin/v1/dashboard")
class DashboardController(
    private val productRepo: ProductRepository,
    private val categoryRepo: CategoryRepository,
    private val mongo: ReactiveMongoTemplate
) {

    // 8.1 Summary
    @GetMapping("/summary")
    fun summary(): Mono<ResponseEntity<Map<String, Any?>>> {
        // start of today in UTC
        val startOfDay = Instant.now().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant()

        val totalProductsMono = productRepo.count().map { it }
        val publishedProductsMono = productRepo.findAll().filter { p -> p.status == "PUBLISHED" }.count()
        val outOfStockMono = productRepo.findAll()
            .filter { p ->
                p.inventory?.let { inv ->
                    val sq = inv.stockQuantity
                    val lt = inv.lowStockThreshold
                    sq != null && lt != null && sq <= lt
                } ?: false
            }.count()
        val totalCategoriesMono = categoryRepo.count()

        val totalOrdersMono = mongo.count(org.springframework.data.mongodb.core.query.Query(), "orders")
            .onErrorReturn(0L) // if orders collection missing
        val todayOrdersMono = mongo.count(org.springframework.data.mongodb.core.query.Query(Criteria.where("createdAt").gte(startOfDay)), "orders")
            .onErrorReturn(0L)

        val todayRevenueMono = mongo.find(org.springframework.data.mongodb.core.query.Query(Criteria.where("createdAt").gte(startOfDay)), Document::class.java, "orders")
            .collectList()
            .map { docs ->
                docs.fold(0.0) { acc, d ->
                    val amt = when (val v = d.get("totalAmount")) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    acc + amt
                }
            }
            .onErrorReturn(0.0)

        return Mono.zip(totalProductsMono, publishedProductsMono, outOfStockMono, totalCategoriesMono, totalOrdersMono, todayOrdersMono, todayRevenueMono)
            .map { tuple ->
                val body = mapOf(
                    "totalProducts" to tuple.t1,
                    "publishedProducts" to tuple.t2,
                    "outOfStockProducts" to tuple.t3,
                    "totalCategories" to tuple.t4,
                    "totalOrders" to tuple.t5,
                    "todayOrders" to tuple.t6,
                    "todayRevenue" to tuple.t7
                )
                ResponseEntity.ok(mapOf("success" to true, "data" to body, "error" to null))
            }
    }

    // 8.2 Top products (by sold quantity) - attempts to aggregate from "orders" collection.
    @GetMapping("/top-products")
    fun topProducts(): Mono<ResponseEntity<Map<String, Any?>>> {
        // Aggregation:
        // unwind items, group by items.productId summing items.quantity, sort desc, limit 10
        val unwind = unwind("items")
        val group = group("items.productId").sum("items.quantity").`as`("totalQty")
        val sort = sort(Sort.by(Sort.Direction.DESC, "totalQty"))
        val limit = limit(10)
        val agg = newAggregation(unwind, group, sort, limit)

        return mongo.aggregate(agg, "orders", Document::class.java)
            .collectList()
            .flatMapMany { aggs ->
                // if no results, return empty list
                if (aggs.isEmpty()) Flux.empty<Document>()
                else Flux.fromIterable(aggs)
            }
            .flatMap { doc ->
                val prodId = doc.getString("_id") ?: doc.get("_id")?.toString()
                val totalQty = when (val v = doc.get("totalQty")) {
                    is Number -> v.toLong()
                    else -> (doc.get("totalQty") as? Number)?.toLong() ?: 0L
                }
                if (prodId == null) {
                    Mono.empty()
                } else {
                    productRepo.findById(prodId)
                        .map { p ->
                            mapOf(
                                "productId" to p.id,
                                "name" to p.name,
                                "thumbnailUrl" to (p.images?.firstOrNull()?.url),
                                "soldQuantity" to totalQty
                            )
                        }.switchIfEmpty(Mono.just(mapOf("productId" to prodId, "name" to "Unknown", "thumbnailUrl" to null, "soldQuantity" to totalQty)))
                }
            }
            .collectList()
            .map { list ->
                ResponseEntity.ok(mapOf("success" to true, "data" to list, "error" to null))
            }
            .onErrorResume { _ ->
                // fallback: if aggregation fails (no orders collection), return empty result
                Mono.just(ResponseEntity.ok(mapOf("success" to true, "data" to emptyList<Map<String, Any>>(), "error" to null)))
            }
    }
}
