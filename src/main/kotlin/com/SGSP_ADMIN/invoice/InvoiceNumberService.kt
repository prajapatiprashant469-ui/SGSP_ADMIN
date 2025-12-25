package com.SGSP_ADMIN.invoice

import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Service
class InvoiceNumberService(
    private val mongoTemplate: MongoTemplate
) {

    fun nextInvoiceNo(): String {

        val query = Query.query(Criteria.where("_id").`is`("INVOICE"))

        val update = Update().inc("lastInvoiceNo", 1)

        val options = FindAndModifyOptions()
            .upsert(true)        // create if not exists
            .returnNew(true)     // return updated value

        val seq = mongoTemplate.findAndModify(
            query,
            update,
            options,
            InvoiceSequence::class.java
        )

        return seq!!.lastInvoiceNo.toString()
    }
}
