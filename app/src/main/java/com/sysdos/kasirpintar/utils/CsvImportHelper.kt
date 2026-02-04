package com.sysdos.kasirpintar.utils

import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.ProductVariant
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CsvImportHelper {

    data class ParsedProduct(
        val product: Product,
        val variants: MutableList<ProductVariant> = ArrayList(), // 🔥 MUTABLE agar bisa diupdate via reference
        val parentName: String? = null 
    )

    fun parseCsv(inputStream: InputStream): List<ParsedProduct> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        
        // Map: Kunci (Nama/Barcode) -> Objek Produk
        val productMap = HashMap<String, ParsedProduct>()
        val pendingVariants = ArrayList<TempVariant>()
        val correctProducts = ArrayList<ParsedProduct>() // Simpan list urut agar tidak duplikat saat return

        // Helper to check headers
        var line: String? = reader.readLine()
        
        // Handle Header
        if (line != null && (line.contains("Nama", ignoreCase = true) || line.contains("Name", ignoreCase = true))) {
             // Skip Header
        } else if (line != null) {
            processLine(line, productMap, correctProducts, pendingVariants)
        }

        while (reader.readLine().also { line = it } != null) {
            processLine(line!!, productMap, correctProducts, pendingVariants)
        }

        // Merge Variants into Parents
        for (v in pendingVariants) {
            // Coba Cari via Kode (Barcode) DULU (Lebih Akurat)
            var parent = productMap[v.parentKey] // By Barcode / Exact Name
            
            // Jika tidak ketemu, coba via Nama Normalisasi (misal: "nasigoreng")
            if (parent == null) {
                parent = productMap[normalizeKey(v.parentKey)]
            }

            if (parent != null) {
                parent.variants.add(v.variant)
            }
        }

        return correctProducts
    }

    private data class TempVariant(val parentKey: String, val variant: ProductVariant)

    private fun normalizeKey(name: String): String {
        return name.lowercase().replace("\\s".toRegex(), "")
    }

    private fun processLine(
        line: String, 
        productMap: HashMap<String, ParsedProduct>, 
        correctProducts: ArrayList<ParsedProduct>,
        pendingVariants: ArrayList<TempVariant>
    ) {
        val tokens = line.split(",").map { it.trim() }
        if (tokens.size < 2) return 

        // CSV: Name, Category, Price, Cost, Stock, Unit, Barcode, ParentName/Code
        val name = tokens.getOrElse(0) { "" }
        if (name.isEmpty()) return

        val category = tokens.getOrElse(1) { "" }
        val price = tokens.getOrElse(2) { "0" }.toDoubleOrNull() ?: 0.0
        val cost = tokens.getOrElse(3) { "0" }.toDoubleOrNull() ?: 0.0
        val stock = tokens.getOrElse(4) { "0" }.toIntOrNull() ?: 0
        val unit = tokens.getOrElse(5) { "Pcs" }
        val barcode = tokens.getOrElse(6) { "" }.ifEmpty { null }
        val parentRef = tokens.getOrElse(7) { "" } // Bisa Nama atau Barcode Induk

        if (parentRef.isNotEmpty()) {
            // -> VARIANT
            val variant = ProductVariant(productId = 0, variantName = name, variantPrice = price, variantStock = stock)
            pendingVariants.add(TempVariant(parentRef, variant))
        } else {
            // -> PARENT PRODUCT
            val product = Product(
                name = name, category = category.ifEmpty { "Umum" },
                price = price, costPrice = cost, stock = stock, unit = unit,
                barcode = barcode, isIngredient = false, parentId = 0
            )
            val parsed = ParsedProduct(product)
            correctProducts.add(parsed)

            // Register Keys (Name & Barcode)
            productMap[name] = parsed // Exact Name
            productMap[normalizeKey(name)] = parsed // Normalized Name
            if (!barcode.isNullOrEmpty()) {
                productMap[barcode] = parsed // Barcode linking
            }
        }
    }
}
