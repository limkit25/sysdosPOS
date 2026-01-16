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

class ProductEntryActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private var currentPhotoPath: String? = null
    private var productToEdit: Product? = null

    // UI Elements
    private lateinit var etName: TextInputEditText
    private lateinit var etPrice: TextInputEditText
    private lateinit var etCost: TextInputEditText
    private lateinit var etBarcode: TextInputEditText
    private lateinit var ivProduct: ImageView
    private lateinit var etCategory: AutoCompleteTextView

    // 🔥 VARIANT UI
    private lateinit var cbHasVariant: CheckBox
    private lateinit var btnAddVariant: Button
    private lateinit var llVariantContainer: LinearLayout

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

    private fun initViews() {
        etName = findViewById(R.id.etProductName)
        etPrice = findViewById(R.id.etProductPrice)
        etCost = findViewById(R.id.etProductCost)
        etBarcode = findViewById(R.id.etProductBarcode)
        ivProduct = findViewById(R.id.ivProductImage)
        etCategory = findViewById(R.id.etProductCategory)

        cbHasVariant = findViewById(R.id.cbHasVariant)
        btnAddVariant = findViewById(R.id.btnAddVariantRow)
        llVariantContainer = findViewById(R.id.llVariantContainer)
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

        findViewById<View>(R.id.btnAddCategoryLink).setOnClickListener {
            startActivity(Intent(this, CategoryActivity::class.java))
        }

        // 🔥 LOGIKA CHECKBOX VARIAN
        cbHasVariant.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                btnAddVariant.visibility = View.VISIBLE
                llVariantContainer.visibility = View.VISIBLE
                etPrice.hint = "Harga Dasar (Opsional)" // Hanya ganti hint

                // Tambah baris kosong JIKA container masih kosong
                if (llVariantContainer.childCount == 0) addVariantRow()
            } else {
                btnAddVariant.visibility = View.GONE
                llVariantContainer.visibility = View.GONE
                llVariantContainer.removeAllViews() // Hapus semua jika di-uncheck

                etPrice.isEnabled = true
                etPrice.hint = "Harga Jual"
            }
        }

        btnAddVariant.setOnClickListener { addVariantRow() }

        findViewById<Button>(R.id.btnSaveProduct).setOnClickListener {
            saveProduct()
        }
    }

    private fun loadCategories() {
        viewModel.allCategories.observe(this) { categories ->
            val safeCategories = categories ?: emptyList()
            val categoryNames = safeCategories.map { it.name }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoryNames)
            etCategory.setAdapter(adapter)
            etCategory.setOnClickListener { etCategory.showDropDown() }
        }
    }

    // 🔥 UPDATE FUNGSI INI: Bisa terima parameter (untuk isi data saat Edit)
    private fun addVariantRow(name: String = "", price: Double = 0.0) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_variant_entry, llVariantContainer, false)

        val etVName = view.findViewById<EditText>(R.id.etVariantName)
        val etVPrice = view.findViewById<EditText>(R.id.etVariantPrice)
        val btnRemove = view.findViewById<ImageButton>(R.id.btnRemoveVariant)

        // Jika ada data (dari Edit), isi field-nya
        if (name.isNotEmpty()) etVName.setText(name)
        if (price > 0) etVPrice.setText(price.toInt().toString())

        btnRemove.setOnClickListener {
            llVariantContainer.removeView(view)
        }

        llVariantContainer.addView(view)
    }

    // 🔥 UPDATE FUNGSI INI: Load Varian dari Database
    private fun checkEditMode() {
        if (intent.hasExtra("PRODUCT_TO_EDIT")) {
            productToEdit = intent.getParcelableExtra("PRODUCT_TO_EDIT")
            productToEdit?.let { product ->
                etName.setText(product.name)
                etPrice.setText(product.price.toInt().toString())
                etCost.setText(product.costPrice.toInt().toString())
                etBarcode.setText(product.barcode)
                etCategory.setText(product.category, false)

                currentPhotoPath = product.imagePath
                if (currentPhotoPath != null) setPic()

                findViewById<Button>(R.id.btnSaveProduct).text = "UPDATE PRODUK"

                // 🔥 LOAD VARIAN DARI DATABASE
                viewModel.getVariants(product.id).observe(this) { variants ->
                    if (!variants.isNullOrEmpty()) {
                        // 1. Centang Checkbox (ini akan trigger listener, nambah 1 baris kosong)
                        if (!cbHasVariant.isChecked) cbHasVariant.isChecked = true

                        // 2. Bersihkan baris kosong default tadi
                        llVariantContainer.removeAllViews()

                        // 3. Masukkan data varian asli dari database
                        for (v in variants) {
                            addVariantRow(v.variantName, v.variantPrice)
                        }
                    }
                }
            }
        }
    }

    private fun saveProduct() {
        val name = etName.text.toString()
        val priceStr = etPrice.text.toString()
        val costStr = etCost.text.toString()
        val category = etCategory.text.toString()
        val barcode = etBarcode.text.toString()

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

        val newProduct = Product(
            id = productToEdit?.id ?: 0,
            name = name,
            price = price, // Harga user tetap tersimpan
            costPrice = cost,
            stock = stock,
            category = category.ifEmpty { "Lainnya" },
            barcode = barcode.ifEmpty { null },
            imagePath = currentPhotoPath,
            supplier = "-"
        )

        // --- SIMPAN / UPDATE ---
        if (productToEdit == null) {
            // INSERT BARU
            viewModel.insertProductWithCallback(newProduct) { newId ->
                if (variantsList.isNotEmpty()) {
                    val finalVariants = variantsList.map { it.copy(productId = newId.toInt()) }
                    viewModel.insertVariants(finalVariants)
                }
                runOnUiThread {
                    Toast.makeText(this, "✅ Produk Disimpan! Restock via Menu Purchasing.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        } else {
            // === MODE UPDATE ===

            // 1. Update Data Induk (Nama, Kategori, dll)
            viewModel.update(newProduct)

            // 2. Update Varian (HAPUS LAMA -> INPUT BARU)
            if (cbHasVariant.isChecked && variantsList.isNotEmpty()) {
                val editId = productToEdit!!.id

                // Pastikan semua varian punya ID Produk yang benar & ID Varian direset jadi 0
                val finalVariants = variantsList.map {
                    it.copy(productId = editId, variantId = 0)
                }

                // 🔥 UPDATE VARIAN (Hapus Lama -> Simpan Baru)
                viewModel.updateVariants(editId, finalVariants)
            } else {
                // Jika user uncheck "Punya Varian", maka hapus semua varian lama
                viewModel.updateVariants(productToEdit!!.id, emptyList())
            }

            Toast.makeText(this, "✅ Produk Diupdate!", Toast.LENGTH_SHORT).show()
            finish()
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