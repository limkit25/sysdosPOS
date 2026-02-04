package com.sysdos.kasirpintar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.ProductVariant
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.sysdos.kasirpintar.ui.dialog.RecipeSelectionDialog
import com.sysdos.kasirpintar.data.model.Recipe

class ProductEntryActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private var currentPhotoPath: String? = null
    private var productToEdit: Product? = null
    
    // 🔥 DATA RESEP (Phase 24)
    private var currentRecipeList = ArrayList<Recipe>()

    // UI Elements
    private lateinit var etName: TextInputEditText
    private lateinit var etPrice: TextInputEditText
    private lateinit var etCost: TextInputEditText
    private lateinit var etBarcode: TextInputEditText
    private lateinit var spUnit: Spinner // 🔥 REPLACED etUnit
    private lateinit var ivProduct: ImageView
    private lateinit var spCategory: Spinner

    // 🔥 VARIANT UI
    private lateinit var cbHasVariant: CheckBox
    private lateinit var btnAddVariant: Button
    private lateinit var llVariantContainer: LinearLayout

    // 🔥 RECIPE UI (Phase 24)
    private lateinit var cbIsIngredient: CheckBox
    private lateinit var llRecipeSection: LinearLayout
    private lateinit var btnAddIngredient: Button
    private lateinit var tvRecipeSummary: TextView

    // LAUNCHERS
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setPic()
        }
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            etBarcode.setText(result.contents)
            Toast.makeText(this, "Scan Sukses!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_entry)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        initViews()
        setupListeners()
        loadCategories()
        checkEditMode() // 🔥 Cek apakah ini mode Edit?
    }

    private fun setupUnitSpinner() {
        val units = listOf("Pcs", "Kg", "Gr", "Liter", "Ml", "Box", "Dus", "Kaleng", "Botol", "Paket")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, units)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spUnit.adapter = adapter
    }

    private fun initViews() {
        etName = findViewById(R.id.etProductName)
        etPrice = findViewById(R.id.etProductPrice)
        etCost = findViewById(R.id.etProductCost)
        etBarcode = findViewById(R.id.etProductBarcode)
        spUnit = findViewById(R.id.spProductUnit) // 🔥 BIND SPINNER
        setupUnitSpinner()
        ivProduct = findViewById(R.id.ivProductImage)
        spCategory = findViewById(R.id.spProductCategory)

        cbHasVariant = findViewById(R.id.cbHasVariant)
        btnAddVariant = findViewById(R.id.btnAddVariantRow)
        llVariantContainer = findViewById(R.id.llVariantContainer)

        // 🔥 BIND RECIPE UI
        cbIsIngredient = findViewById(R.id.cbIsIngredient)
        llRecipeSection = findViewById(R.id.llRecipeSection)
        btnAddIngredient = findViewById(R.id.btnAddIngredient)
        tvRecipeSummary = findViewById(R.id.tvRecipeSummary)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener { checkCameraPermissionAndOpen() }

        findViewById<View>(R.id.btnScanBarcode).setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(ScanActivity::class.java)
            scanLauncher.launch(options)
        }

        // 🔥 LOGIKA CHECKBOX VARIAN
        cbHasVariant.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                btnAddVariant.visibility = View.VISIBLE
                llVariantContainer.visibility = View.VISIBLE
                etPrice.hint = "Harga Dasar (Opsional)"
                
                // Jika punya varian, biasanya bukan bahan baku
                cbIsIngredient.isChecked = false
                
                // 🔥 UPDATE: Tetap tampilkan resep meskipun punya varian (Resep Induk)
                if (!cbIsIngredient.isChecked) llRecipeSection.visibility = View.VISIBLE

                if (llVariantContainer.childCount == 0) addVariantRow()
            } else {
                btnAddVariant.visibility = View.GONE
                llVariantContainer.visibility = View.GONE
                llVariantContainer.removeAllViews()

                etPrice.isEnabled = true
                etPrice.hint = "Harga Jual"
                
                // Kembalikan visibilitas resep jika bukan bahan baku
                if (!cbIsIngredient.isChecked) llRecipeSection.visibility = View.VISIBLE
            }
        }
        
        // 🔥 LOGIKA CHECKBOX BAHAN BAKU
        cbIsIngredient.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Jika ini bahan baku, maka TIDAK PUNYA RESEP
                llRecipeSection.visibility = View.GONE
                cbHasVariant.isChecked = false // Bahan baku biasanya tidak bervarian di sini
                etCost.hint = "Harga Beli (HPP) per Unit"
            } else {
                // Jika Produk Jual, BOLEH punya resep
                if (!cbHasVariant.isChecked) llRecipeSection.visibility = View.VISIBLE
                etCost.hint = "Harga Modal (Otomatis jika ada Resep)"
            }
        }

        btnAddVariant.setOnClickListener { addVariantRow() }
        
        // 🔥 TOMBOL ATUR RESEP
        btnAddIngredient.setOnClickListener {
            val allProds = viewModel.allProducts.value ?: emptyList()
            RecipeSelectionDialog(this, allProds, currentRecipeList) { newRecipe ->
                currentRecipeList.clear()
                currentRecipeList.addAll(newRecipe)
                updateRecipeSummary()
            }.show()
        }

        findViewById<Button>(R.id.btnSaveProduct).setOnClickListener {
            saveProduct()
        }
    }
    
    private fun updateRecipeSummary() {
        if (currentRecipeList.isEmpty()) {
            tvRecipeSummary.text = "Belum ada resep diatur."
        } else {
            val sb = StringBuilder()
            var estimatedCost = 0.0
            val allProds = viewModel.allProducts.value ?: emptyList()

            currentRecipeList.forEach { r ->
                val ingName = allProds.find { it.id == r.ingredientId }?.name ?: "Unknown"
                sb.append("- ${r.quantity} ${r.unit} $ingName\n")
                
                // Hitung estimasi HPP
                val ingCost = allProds.find { it.id == r.ingredientId }?.costPrice ?: 0.0
                // Asumsi costPrice itu harga per 1 unit
                estimatedCost += (ingCost * r.quantity)
            }
            sb.append("\nEstimasi HPP: Rp ${estimatedCost.toInt()}")
            tvRecipeSummary.text = sb.toString()
            
            // Auto-fill Cost Price field
            etCost.setText(estimatedCost.toInt().toString())
        }
    }

    private fun loadCategories() {
        viewModel.allCategories.observe(this) { categories ->
            val safeCategories = categories ?: emptyList()
            val categoryNames = safeCategories.map { it.name }.toMutableList()
            if (categoryNames.isEmpty()) categoryNames.add("Umum")

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spCategory.adapter = adapter

            productToEdit?.let { p ->
                val position = categoryNames.indexOf(p.category)
                if (position >= 0) spCategory.setSelection(position)
            }
        }

        // 🔥 PENTING: Observe allProducts agar data ter-load dari DB
        // (Supaya saat klik tombol resep, viewModel.allProducts.value tidak kosong)
        // 🔥 PENTING: Observe allProducts agar data ter-load dari DB
        viewModel.allProducts.observe(this) { 
            // Refresh summary jika data produk baru masuk (untuk fix nama bahan unknown)
            if (currentRecipeList.isNotEmpty()) {
                updateRecipeSummary()
            }
        }
    }

    private fun addVariantRow(name: String = "", price: Double = 0.0) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_variant_entry, llVariantContainer, false)
        val etVName = view.findViewById<EditText>(R.id.etVariantName)
        val etVPrice = view.findViewById<EditText>(R.id.etVariantPrice)
        val btnRemove = view.findViewById<ImageButton>(R.id.btnRemoveVariant)

        if (name.isNotEmpty()) etVName.setText(name)
        if (price > 0) etVPrice.setText(price.toInt().toString())

        btnRemove.setOnClickListener {
            llVariantContainer.removeView(view)
        }
        llVariantContainer.addView(view)
    }

    private fun checkEditMode() {
        if (intent.hasExtra("PRODUCT_TO_EDIT")) {
            productToEdit = intent.getParcelableExtra("PRODUCT_TO_EDIT")
            productToEdit?.let { product ->
                etName.setText(product.name)
                etPrice.setText(product.price.toInt().toString())
                etCost.setText(product.costPrice.toInt().toString())
                etBarcode.setText(product.barcode)
                // 🔥 LOAD UNIT
                val units = listOf("Pcs", "Kg", "Gr", "Liter", "Ml", "Box", "Dus", "Kaleng", "Botol", "Paket")
                val uIndex = units.indexOf(product.unit)
                if (uIndex >= 0) spUnit.setSelection(uIndex)

                currentPhotoPath = product.imagePath
                if (currentPhotoPath != null) setPic()

                findViewById<Button>(R.id.btnSaveProduct).text = "UPDATE PRODUK"
                
                // 🔥 LOAD INGREDIENT STATUS
                cbIsIngredient.isChecked = product.isIngredient

                // 🔥 LOAD VARIAN DARI DATABASE
                viewModel.getVariants(product.id).observe(this) { variants ->
                    if (!variants.isNullOrEmpty()) {
                        if (!cbHasVariant.isChecked) cbHasVariant.isChecked = true
                        llVariantContainer.removeAllViews()
                        for (v in variants) {
                            addVariantRow(v.variantName, v.variantPrice)
                        }
                    }
                }
                
                // 🔥 LOAD RESEP DARI DATABASE
                if (!product.isIngredient) {
                    android.util.Log.d("RecipeDebug", "UI: Requesting recipes for ID: ${product.id}")
                    viewModel.getRecipeForProduct(product.id) { recipes ->
                        android.util.Log.d("RecipeDebug", "UI: Received ${recipes.size} recipes for ID: ${product.id}")
                        currentRecipeList.clear()
                        currentRecipeList.addAll(recipes)
                        updateRecipeSummary()
                    }
                }
            }
        }
    }

    private fun saveProduct() {
        val name = etName.text.toString()
        val priceStr = etPrice.text.toString()
        val costStr = etCost.text.toString()
        val category = spCategory.selectedItem?.toString() ?: "Umum"
        val barcode = etBarcode.text.toString()
        val unit = spUnit.selectedItem.toString() // 🔥 GET UNIT FROM SPINNER

        if (name.isEmpty()) {
            etName.error = "Wajib diisi"
            return
        }

        // --- AMBIL DATA VARIAN ---
        val variantsList = ArrayList<ProductVariant>()
        if (cbHasVariant.isChecked) {
            for (i in 0 until llVariantContainer.childCount) {
                val row = llVariantContainer.getChildAt(i)
                val etVName = row.findViewById<EditText>(R.id.etVariantName)
                val etVPrice = row.findViewById<EditText>(R.id.etVariantPrice)

                val vName = etVName.text.toString().trim()
                val vPrice = etVPrice.text.toString().toDoubleOrNull() ?: 0.0

                if (vName.isNotEmpty() && vPrice >= 0) {
                    variantsList.add(ProductVariant(productId = 0, variantName = vName, variantPrice = vPrice))
                }
            }
            if (variantsList.isEmpty()) {
                Toast.makeText(this, "Isi minimal 1 varian!", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            if (priceStr.isEmpty()) {
                etPrice.error = "Wajib diisi"
                return
            }
        }

        val price = priceStr.toDoubleOrNull() ?: 0.0
        val cost = costStr.toDoubleOrNull() ?: 0.0
        val stock = productToEdit?.stock ?: 0
        
        val isIngredient = cbIsIngredient.isChecked
        
        android.util.Log.d("RecipeDebug", "Saving Product: isIngredient=$isIngredient, RecipeCount=${currentRecipeList.size}, CalculatedTrackStock=${!isIngredient && currentRecipeList.isEmpty()}")

        val newProduct = Product(
            id = productToEdit?.id ?: 0,
            name = name,
            price = price,
            costPrice = cost,
            stock = stock,
            category = category.ifEmpty { "Lainnya" },
            barcode = barcode.ifEmpty { null },
            imagePath = currentPhotoPath,
            supplier = "-",
            
            // 🔥 DATA BARU
            isIngredient = isIngredient,
            // Jika bahan baku -> False
            // Jika punya resep -> False (Stok Virtual / Unlimited di POS, validasi saat checkout)
            // Jika produk biasa -> True
            trackStock = !isIngredient && currentRecipeList.isEmpty(), 
            unit = unit.ifEmpty { "Pcs" } // 🔥 SIMPAN UNIT
        )

        // --- SIMPAN / UPDATE ---
        // --- SIMPAN / UPDATE ---
        if (productToEdit == null) {
            // INSERT BARU
            viewModel.insertProductWithCallback(newProduct) { newId ->
                // 1. Simpan Varian
                if (variantsList.isNotEmpty()) {
                    val finalVariants = variantsList.map { it.copy(productId = newId.toInt()) }
                    viewModel.insertVariants(finalVariants)
                }
                
                // 2. 🔥 Simpan Resep
                if (!isIngredient && currentRecipeList.isNotEmpty()) {
                    val finalRecipes = currentRecipeList.map { it.copy(productId = newId.toInt()) }
                    viewModel.updateRecipes(newId.toInt(), finalRecipes) {
                        runOnUiThread {
                            Toast.makeText(this, "✅ Produk Disimpan!", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "✅ Produk Disimpan!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        } else {
            // === MODE UPDATE ===
            // 🔥 UPDATE PRODUK DULU -> TUNGGU SELESAI -> BARU UPDATE RESEP
            viewModel.updateProductWithCallback(newProduct) {
                val editId = productToEdit!!.id

                // 1. Update Varian
                if (cbHasVariant.isChecked && variantsList.isNotEmpty()) {
                    val finalVariants = variantsList.map { it.copy(productId = editId, variantId = 0) }
                    viewModel.updateVariants(editId, finalVariants)
                } else {
                    viewModel.updateVariants(editId, emptyList())
                }
                
                // 2. 🔥 Update Resep
                if (!isIngredient) {
                     // Update resep (Hapus lama -> Insert baru)
                     android.util.Log.d("RecipeDebug", "UI: Saving ${currentRecipeList.size} recipes for Update ID: $editId")
                     val finalRecipes = currentRecipeList.map { it.copy(productId = editId) }
                     viewModel.updateRecipes(editId, finalRecipes) {
                         runOnUiThread {
                             Toast.makeText(this, "✅ Produk Diupdate!", Toast.LENGTH_SHORT).show()
                             finish()
                         }
                     }
                } else {
                     // Jika berubah jadi bahan baku, hapus resep lama
                     viewModel.updateRecipes(editId, emptyList()) {
                         runOnUiThread {
                             Toast.makeText(this, "✅ Produk Diupdate!", Toast.LENGTH_SHORT).show()
                             finish()
                         }
                     }
                }
            }
        }
    }

    // ... (Bagian Kamera & Izin tidak berubah) ...
    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        } else {
            dispatchTakePictureIntent()
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try { createImageFile() } catch (ex: Exception) { null }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(this, "${packageName}.provider", it)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    takePictureLauncher.launch(takePictureIntent)
                }
            }
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun setPic() {
        currentPhotoPath?.let { path ->
            val bitmap = BitmapFactory.decodeFile(path)
            ivProduct.setImageBitmap(bitmap)
        }
    }
}