package com.sysdos.kasirpintar.data.model

data class CartItem(
    val product: Product,
    var quantity: Int = 0
)