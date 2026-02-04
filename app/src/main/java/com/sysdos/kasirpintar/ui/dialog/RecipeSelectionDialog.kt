package com.sysdos.kasirpintar.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sysdos.kasirpintar.R
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.Recipe

class RecipeSelectionDialog(
    context: Context,
    private val allProducts: List<Product>, // All products to filter ingredients from
    private val currentRecipe: List<Recipe>,
    private val onSave: (List<Recipe>) -> Unit
) : Dialog(context) {

    private val tempRecipe = ArrayList<Recipe>(currentRecipe) // Clone for editing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_recipe_selection)

        val rvIngredients = findViewById<RecyclerView>(R.id.rvIngredients)
        val llEmptyState = findViewById<LinearLayout>(R.id.llEmptyState) // 🔥 FIND VIEW
        val btnAddInfo = findViewById<Button>(R.id.btnAddInfo)
        val btnSave = findViewById<Button>(R.id.btnSaveRecipe)

        // Filter only ingredients
        val ingredientsList = allProducts.filter { it.isIngredient }
        
        if (ingredientsList.isEmpty()) {
            rvIngredients.visibility = View.GONE
            llEmptyState.visibility = View.VISIBLE
        } else {
            rvIngredients.visibility = View.VISIBLE
            llEmptyState.visibility = View.GONE
        }

        // Adapter setup
        val adapter = RecipeAdapter(ingredientsList, tempRecipe)
        rvIngredients.layoutManager = LinearLayoutManager(context)
        rvIngredients.adapter = adapter

        btnSave.setOnClickListener {
            onSave(adapter.getFinalRecipe())
            dismiss()
        }
        
        btnAddInfo.setOnClickListener {
             Toast.makeText(context, "Pilih bahan dari list dan isi jumlah pemakaian per porsi.", Toast.LENGTH_LONG).show()
        }
    }
}

// Simple Adapter inside (or separate)
class RecipeAdapter(
    private val ingredients: List<Product>,
    private val initialRecipes: List<Recipe>
) : RecyclerView.Adapter<RecipeAdapter.ViewHolder>() {

    // Map: IngredientID -> Qty
    private val currentSelection = HashMap<Int, Double>()

    init {
        for (r in initialRecipes) {
            currentSelection[r.ingredientId] = r.quantity
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvIngredientName)
        val etQty: EditText = view.findViewById(R.id.etIngredientQty)
        val tvUnit: TextView = view.findViewById(R.id.tvIngredientUnit)
        val tvQtyUnit: TextView = view.findViewById(R.id.tvQtyUnit) // 🔥 NEW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe_select, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ingredient = ingredients[position]
        holder.tvName.text = ingredient.name
        holder.tvUnit.text = "Stok: ${ingredient.stock} ${ingredient.unit}" // 🔥 Update Stock display
        holder.tvQtyUnit.text = ingredient.unit // 🔥 Show Unit next to Input

        // Hapus listener lama untuk hindari bug recycling
        if (holder.etQty.tag is android.text.TextWatcher) {
            holder.etQty.removeTextChangedListener(holder.etQty.tag as android.text.TextWatcher)
        }

        // Set value awal
        if (currentSelection.containsKey(ingredient.id)) {
            val dbVal = currentSelection[ingredient.id]!!.toString()
            if (holder.etQty.text.toString() != dbVal) {
                holder.etQty.setText(dbVal)
            }
        } else {
            holder.etQty.setText("")
        }

        // Buat Listener Baru
        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val qty = s.toString().toDoubleOrNull() ?: 0.0
                if (qty > 0) {
                    currentSelection[ingredient.id] = qty
                } else {
                    currentSelection.remove(ingredient.id)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        // Pasang & Simpan di Tag
        holder.etQty.addTextChangedListener(watcher)
        holder.etQty.tag = watcher
    }

    override fun getItemCount() = ingredients.size

    fun getFinalRecipe(): List<Recipe> {
        val list = ArrayList<Recipe>()
        for ((id, qty) in currentSelection) {
            val unit = ingredients.find { it.id == id }?.unit ?: "unit" // 🔥 Get accurate unit
            list.add(Recipe(productId = 0, ingredientId = id, quantity = qty, unit = unit))
        }
        return list
    }
}
