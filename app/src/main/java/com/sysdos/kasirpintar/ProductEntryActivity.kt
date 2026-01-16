package com.sysdos.kasirpintar

import android.Manifest
import android.app.Activity
import android.content.Context
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sysdos.kasirpintar.data.model.Category
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
        checkEditMode()
    }

    private fun initViews() {
        // BINDING VIEW (Hapus etStock)
        etName = findViewById(R.id.etProductName)
        etPrice = findViewById(R.id.etProductPrice)
        etCost = findViewById(R.id.etProductCost)
        etBarcode = findViewById(R.id.etProductBarcode)
        ivProduct = findViewById(R.id.ivProductImage)
        etCategory = findViewById(R.id.etProductCategory)

        // Varian Bind
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

                // Matikan Input Harga Induk (Karena pakai harga varian)
                etPrice.isEnabled = false
                etPrice.setText("0")
                etPrice.hint = "Diatur di Varian"

                // Tambah 1 baris default biar gak kosong
                if (llVariantContainer.childCount == 0) addVariantRow()
            } else {
                btnAddVariant.visibility = View.GONE
                llVariantContainer.visibility = View.GONE
                llVariantContainer.removeAllViews()

                // Nyalakan Input Harga Induk
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

    // --- LOGIKA VARIAN ---
    private fun addVariantRow() {
        val view = LayoutInflater.from(this).inflate(R.layout.item_variant_entry, llVariantContainer, false)

        val btnRemove = view.findViewById<ImageButton>(R.id.btnRemoveVariant)
        btnRemove.setOnClickListener {
            llVariantContainer.removeView(view)
        }

        llVariantContainer.addView(view)
    }

    private fun checkEditMode() {
        if (intent.hasExtra("PRODUCT_TO_EDIT")) {
            productToEdit = intent.getParcelableExtra("PRODUCT_TO_EDIT")
            productToEdit?.let {
                etName.setText(it.name)
                etPrice.setText(it.price.toInt().toString())
                etCost.setText(it.costPrice.toInt().toString())
                // Stok TIDAK BOLEH diedit di sini, jadi tidak ditampilkan
                etBarcode.setText(it.barcode)
                etCategory.setText(it.category, false)

                currentPhotoPath = it.imagePath
                if (currentPhotoPath != null) setPic()

                findViewById<Button>(R.id.btnSaveProduct).text = "UPDATE PRODUK"
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

        // --- VALIDASI VARIAN ---
        val variantsList = ArrayList<ProductVariant>()
        if (cbHasVariant.isChecked) {
            for (i in 0 until llVariantContainer.childCount) {
                val row = llVariantContainer.getChildAt(i)
                val etVName = row.findViewById<EditText>(R.id.etVariantName)
                val etVPrice = row.findViewById<EditText>(R.id.etVariantPrice)

                val vName = etVName.text.toString().trim()
                val vPrice = etVPrice.text.toString().toDoubleOrNull() ?: 0.0

                if (vName.isNotEmpty() && vPrice > 0) {
                    variantsList.add(ProductVariant(productId = 0, variantName = vName, variantPrice = vPrice))
                }
            }
            if (variantsList.isEmpty()) {
                Toast.makeText(this, "Isi minimal 1 varian!", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            // Kalau bukan varian, harga wajib isi
            if (priceStr.isEmpty()) {
                etPrice.error = "Wajib diisi"
                return
            }
        }

        val price = priceStr.toDoubleOrNull() ?: 0.0
        val cost = costStr.toDoubleOrNull() ?: 0.0
        // 🔥 STOK SELALU AMBIL DATA LAMA (EDIT) ATAU 0 (BARU)
        val stock = productToEdit?.stock ?: 0

        // Objek Produk Utama
        val newProduct = Product(
            id = productToEdit?.id ?: 0,
            name = name,
            price = if(cbHasVariant.isChecked) 0.0 else price,
            costPrice = cost,
            stock = stock, // Stok aman, tidak diubah dari sini
            category = category.ifEmpty { "Lainnya" },
            barcode = barcode.ifEmpty { null },
            imagePath = currentPhotoPath,
            supplier = "-"
        )

        // --- PROSES SIMPAN ---
        if (productToEdit == null) {
            // INSERT BARU
            viewModel.insertProductWithCallback(newProduct) { newId ->
                // Jika ada varian, simpan variannya dengan ID baru
                if (variantsList.isNotEmpty()) {
                    val finalVariants = variantsList.map { it.copy(productId = newId.toInt()) }
                    viewModel.insertVariants(finalVariants)
                }

                runOnUiThread {
                    Toast.makeText(this, "✅ Produk Disimpan! Silakan Restock via Menu Purchasing.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        } else {
            // UPDATE
            viewModel.update(newProduct)
            Toast.makeText(this, "✅ Produk Diupdate!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // --- LOGIKA KAMERA ---
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