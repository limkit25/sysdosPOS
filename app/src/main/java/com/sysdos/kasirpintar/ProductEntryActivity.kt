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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView // Pastikan Import Ini
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
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
    private lateinit var etStock: TextInputEditText
    private lateinit var etBarcode: TextInputEditText
    private lateinit var ivProduct: ImageView

    // 1. UBAH TIPE JADI AUTOCOMPLETE (Dropdown)
    private lateinit var etCategory: AutoCompleteTextView
    private lateinit var etSupplier: AutoCompleteTextView

    // --- LAUNCHER KAMERA ---
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setPic()
        }
    }

    // --- LAUNCHER SCANNER ---
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

        // BINDING VIEW
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        etName = findViewById(R.id.etProductName)
        etPrice = findViewById(R.id.etProductPrice)
        etCost = findViewById(R.id.etProductCost)
        etStock = findViewById(R.id.etProductStock)
        etBarcode = findViewById(R.id.etProductBarcode)
        ivProduct = findViewById(R.id.ivProductImage)

        // Setup Input Dropdown
        etCategory = findViewById(R.id.etProductCategory)
        etSupplier = findViewById(R.id.etProductSupplier) // ID sama, tapi tipe View beda

        val btnTakePhoto = findViewById<Button>(R.id.btnTakePhoto)
        val btnSave = findViewById<Button>(R.id.btnSaveProduct)
        val btnScan = findViewById<ImageButton>(R.id.btnScanBarcode)
        val btnAddCategory = findViewById<ImageButton>(R.id.btnAddCategoryLink)

        btnBack.setOnClickListener { finish() }

        // 2. ISI DROPDOWN KATEGORI
        viewModel.allCategories.observe(this) { categories ->
            val safeCategories: List<Category> = categories ?: emptyList()
            val categoryNames = safeCategories.map { it.name }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoryNames)
            etCategory.setAdapter(adapter)
        }

        // 3. ISI DROPDOWN SUPPLIER (LOGIKA BARU)
        viewModel.allSuppliers.observe(this) { suppliers ->
            // Ambil nama supplier saja untuk ditampilkan di dropdown
            val supplierNames = suppliers.map { it.name }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, supplierNames)
            etSupplier.setAdapter(adapter)

            // Opsional: Langsung buka dropdown saat diklik
            etSupplier.setOnClickListener { etSupplier.showDropDown() }
        }

        btnAddCategory.setOnClickListener {
            startActivity(Intent(this, CategoryActivity::class.java))
        }

        // CEK MODE EDIT
        if (intent.hasExtra("PRODUCT_TO_EDIT")) {
            productToEdit = intent.getParcelableExtra("PRODUCT_TO_EDIT")
            productToEdit?.let {
                etName.setText(it.name)
                etPrice.setText(it.price.toInt().toString())
                etCost.setText(it.costPrice.toInt().toString())
                etStock.setText(it.stock.toString())
                etBarcode.setText(it.barcode)

                // Isi nilai lama (tanpa filter dropdown dulu)
                etCategory.setText(it.category, false)
                etSupplier.setText(it.supplier, false)

                currentPhotoPath = it.imagePath
                if (currentPhotoPath != null) setPic()

                btnSave.text = "UPDATE PRODUK"
            }
        }

        btnTakePhoto.setOnClickListener { checkCameraPermissionAndOpen() }

        btnScan.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(ScanActivity::class.java)
            scanLauncher.launch(options)
        }

        btnSave.setOnClickListener {
            saveProduct()
        }
    }

    private fun saveProduct() {
        val name = etName.text.toString()
        val priceStr = etPrice.text.toString()
        val costStr = etCost.text.toString()
        val stockStr = etStock.text.toString()
        val category = etCategory.text.toString()
        val barcode = etBarcode.text.toString()
        val supplier = etSupplier.text.toString() // Ambil teks supplier

        if (name.isEmpty() || priceStr.isEmpty() || stockStr.isEmpty()) {
            Toast.makeText(this, "Nama, Harga, dan Stok wajib diisi!", Toast.LENGTH_SHORT).show()
            return
        }

        val price = priceStr.toDouble()
        val cost = if (costStr.isNotEmpty()) costStr.toDouble() else 0.0
        val stock = stockStr.toInt()

        if (price < cost) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Potensi Rugi")
                .setMessage("Harga Jual (Rp ${price.toInt()}) lebih murah dari Modal (Rp ${cost.toInt()}).\n\nYakin tetap simpan?")
                .setPositiveButton("Ya, Simpan") { _, _ ->
                    processSave(name, price, cost, stock, category, barcode, supplier)
                }
                .setNegativeButton("Perbaiki Harga", null)
                .show()
            return
        }

        processSave(name, price, cost, stock, category, barcode, supplier)
    }

    private fun processSave(
        name: String,
        price: Double,
        cost: Double,
        stock: Int,
        category: String,
        barcode: String,
        supplier: String
    ) {
        val newProduct = Product(
            id = productToEdit?.id ?: 0,
            name = name,
            price = price,
            costPrice = cost,
            stock = stock,
            category = category.ifEmpty { "Lainnya" },
            barcode = barcode.ifEmpty { null },
            imagePath = currentPhotoPath,
            supplier = supplier.ifEmpty { null }
        )

        if (productToEdit == null) {
            viewModel.insert(newProduct)
            Toast.makeText(this, "✅ Produk Disimpan!", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.update(newProduct)
            Toast.makeText(this, "✅ Produk Diupdate!", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    // (Bagian Kamera tetap sama)
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
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: Exception) {
                    null
                }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.provider",
                        it
                    )
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