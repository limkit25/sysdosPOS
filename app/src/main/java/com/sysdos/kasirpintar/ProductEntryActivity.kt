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
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton // [PENTING] Import ImageButton
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
import com.journeyapps.barcodescanner.ScanIntentResult
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
    private lateinit var etCategory: AutoCompleteTextView
    private lateinit var ivProduct: ImageView

    // --- LAUNCHER KAMERA ---
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setPic()
        }
    }

    // --- LAUNCHER SCANNER ---
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            etBarcode.setText(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_entry)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // 1. BINDING VIEW (Sesuaikan dengan Layout Baru)
        val btnBack = findViewById<ImageButton>(R.id.btnBack) // Tombol Kembali di Header

        etName = findViewById(R.id.etProductName)
        etPrice = findViewById(R.id.etProductPrice)
        etCost = findViewById(R.id.etProductCost)
        etStock = findViewById(R.id.etProductStock)
        etBarcode = findViewById(R.id.etProductBarcode)
        ivProduct = findViewById(R.id.ivProductImage)
        etCategory = findViewById(R.id.etProductCategory)

        // Tombol-tombol Aksi
        val btnTakePhoto = findViewById<Button>(R.id.btnTakePhoto)
        val btnSave = findViewById<Button>(R.id.btnSaveProduct)

        // [PERBAIKAN] Ubah jadi ImageButton (Ikon)
        val btnScan = findViewById<ImageButton>(R.id.btnScanBarcode)
        val btnAddCategory = findViewById<ImageButton>(R.id.btnAddCategoryLink)

        // 2. FUNGSI TOMBOL BACK (HEADER)
        btnBack.setOnClickListener { finish() }

        // 3. LOGIKA KATEGORI DINAMIS
        viewModel.allCategories.observe(this) { categories ->
            val safeCategories: List<Category> = categories ?: emptyList()
            val categoryNames = safeCategories.map { it.name }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoryNames)
            etCategory.setAdapter(adapter)
        }

        // Tombol [+] Kategori
        btnAddCategory.setOnClickListener {
            startActivity(Intent(this, CategoryActivity::class.java))
        }

        // 4. CEK MODE EDIT ATAU BARU
        if (intent.hasExtra("PRODUCT_TO_EDIT")) {
            productToEdit = intent.getParcelableExtra("PRODUCT_TO_EDIT")
            productToEdit?.let {
                etName.setText(it.name)
                etPrice.setText(it.price.toInt().toString())
                etCost.setText(it.costPrice.toInt().toString())
                etStock.setText(it.stock.toString())
                etCategory.setText(it.category, false)
                etBarcode.setText(it.barcode)

                currentPhotoPath = it.imagePath
                if (currentPhotoPath != null) setPic()

                btnSave.text = "UPDATE PRODUK"
            }
        }

        // 5. LISTENERS LAINNYA
        btnTakePhoto.setOnClickListener { checkCameraPermissionAndOpen() }

        btnScan.setOnClickListener {
            val options = ScanOptions()
            options.setPrompt("Scan Barcode Produk")
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity::class.java)
            barcodeLauncher.launch(options)
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
                    processSave(name, price, cost, stock, category, barcode)
                }
                .setNegativeButton("Perbaiki Harga", null)
                .show()
            return
        }

        processSave(name, price, cost, stock, category, barcode)
    }

    private fun processSave(name: String, price: Double, cost: Double, stock: Int, category: String, barcode: String) {
        val newProduct = Product(
            id = productToEdit?.id ?: 0,
            name = name,
            price = price,
            costPrice = cost,
            stock = stock,
            category = category.ifEmpty { "Lainnya" },
            barcode = barcode.ifEmpty { null },
            imagePath = currentPhotoPath
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