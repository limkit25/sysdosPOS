package com.sysdos.kasirpintar.viewmodel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.R
import com.sysdos.kasirpintar.data.model.Product
import java.util.Locale

class ProductAdapter(
    private val onItemClick: (Product) -> Unit,
    private val onItemLongClick: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(DiffCallback()) {

    // Menyimpan jumlah stok di keranjang (untuk update UI stok sisa)
    private var cartCounts: Map<Int, Int> = emptyMap()

    fun updateCartCounts(cartItems: List<Product>) {
        cartCounts = cartItems.groupBy { it.id }.mapValues { it.value.sumOf { item -> item.stock } }
        notifyDataSetChanged()
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        // Pastikan layout yang dipanggil benar: item_product
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // ID View
        private val tvName: TextView = itemView.findViewById(R.id.tvProductName)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        private val tvStock: TextView = itemView.findViewById(R.id.tvStock)
        private val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)

        // Tambahkan ini (Badge)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvCartBadge)

        fun bind(product: Product) {
            tvName.text = product.name
            tvPrice.text = String.format(Locale("id", "ID"), "Rp %,d", product.price.toLong())

            // --- LOGIKA STOK SISA ---
            // Ambil jumlah yg sudah ada di keranjang
            val qtyInCart = cartCounts[product.id] ?: 0

            // Stok Tampil = Stok Asli - Yang ada di keranjang
            val currentStock = product.stock - qtyInCart
            tvStock.text = "Stok: $currentStock"

            if (currentStock <= 0) {
                tvStock.setTextColor(android.graphics.Color.RED)
                tvStock.text = "Habis!"
            } else {
                // Warna normal (misal Biru sesuai XML tadi)
                tvStock.setTextColor(android.graphics.Color.parseColor("#1976D2"))
            }

            // --- LOGIKA BADGE (LINGKARAN MERAH) ---
            if (qtyInCart > 0) {
                tvBadge.visibility = View.VISIBLE // Munculkan
                tvBadge.text = qtyInCart.toString() // Isi angkanya (misal: 2)
            } else {
                tvBadge.visibility = View.GONE // Sembunyikan kalau 0
            }

            // --- LOAD GAMBAR ---
            try {
                if (product.imagePath != null && product.imagePath.isNotEmpty()) {
                    val uri = android.net.Uri.parse(product.imagePath)
                    imgProduct.setImageURI(uri)
                } else {
                    imgProduct.setImageResource(R.mipmap.ic_launcher)
                }
            } catch (e: Exception) {
                imgProduct.setImageResource(R.mipmap.ic_launcher)
            }

            // KLIK ITEM
            itemView.setOnClickListener { onItemClick(product) }

            // KLIK TAHAN
            itemView.setOnLongClickListener {
                onItemLongClick(product)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Product, newItem: Product) = oldItem == newItem
    }
}